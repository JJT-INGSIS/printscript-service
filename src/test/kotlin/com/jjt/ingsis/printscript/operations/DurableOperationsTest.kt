package com.jjt.ingsis.printscript.operations

import com.jjt.ingsis.printscript.execution.ExecutionLimits
import com.jjt.ingsis.printscript.formatting.FormattingLimits
import com.jjt.ingsis.printscript.formatting.FormattingService
import com.jjt.ingsis.printscript.linting.LintingLimits
import com.jjt.ingsis.printscript.linting.LintingService
import com.jjt.ingsis.printscript.processor.ProcessorCapacity
import com.jjt.ingsis.printscript.processor.ProcessorIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private class OperationRedis : GenericContainer<OperationRedis>(DockerImageName.parse("redis:7.4.7-alpine")) {
    init {
        withExposedPorts(6379)
    }
}

class DurableOperationsTest {
    private val settings = OperationsSettings(namespace = "operations:test:${UUID.randomUUID()}")
    private val store = RedisOperationStore(redis, settings)
    private val limits = ExecutionLimits()
    private val identity = ProcessorIdentity({ "a".repeat(64) }, limits, FormattingLimits())
    private val engine =
        OperationEngine(LintingService(LintingLimits()), FormattingService(FormattingLimits()), identity)

    @Test
    fun `a lost start reply is retried by the same ID and survives a new coordinator`() {
        val id = UUID.randomUUID()
        val command = command(OperationKind.LINT)
        coordinator().use { first ->
            assertThat(first.start(id, command)).isInstanceOf(OperationReply.Found::class.java)
            assertThat(first.start(id, command)).isEqualTo(first.find(id))
            assertThat(first.start(id, command.copy(code = "println(2);"))).isEqualTo(OperationReply.Conflict)
        }
        coordinator().use { restarted ->
            restarted.startWorker()
            val result = await(restarted, id)
            assertThat(result.state).isEqualTo(OperationState.COMPLETED)
            assertThat(result.output?.outcome).isEqualTo("LINTED")
            assertThat(result.command).isEqualTo(command)
        }
        val key = "${settings.namespace}:$id"
        assertThat(redis.getExpire(key, TimeUnit.HOURS)).isBetween(167L, 168L)
        assertThat(store.pending()).doesNotContain(id)
    }

    @Test
    fun `cancelling a missing ID prevents a delayed start and terminal expiry is bounded`() {
        coordinator().use { service ->
            val id = UUID.randomUUID()
            val cancelled = (service.cancel(id) as OperationReply.Found).operation
            assertThat(cancelled.state).isEqualTo(OperationState.CANCELLED)
            assertThat(service.start(id, command(OperationKind.FORMAT))).isEqualTo(OperationReply.Found(cancelled))
            assertThat(
                service.start(
                    UUID.randomUUID(),
                    command(OperationKind.LINT).copy(submittedAt = Instant.now().minusSeconds(180)),
                ),
            ).isEqualTo(OperationReply.Expired)
            assertThat(store.pending()).isEmpty()
        }
    }

    @Test
    fun `real formatter output is recovered without repeating a completed operation`() {
        coordinator().use { service ->
            val id = UUID.randomUUID()
            val input =
                command(OperationKind.FORMAT).copy(
                    code = "let saludo:string=\"¡Hola! 🚀\";println(saludo);",
                    rules = mapOf("enforce-spacing-before-colon-in-declaration" to true),
                )
            service.start(id, input)
            service.startWorker()
            val output = await(service, id)
            assertThat(output.output?.code).contains("saludo :string", "¡Hola! 🚀")
            assertThat(service.start(id, input)).isEqualTo(OperationReply.Found(output))
        }
    }

    @Test
    fun `an incomplete predecessor is recovered only with the same processor revision`() {
        val id = UUID.randomUUID()
        val input = command(OperationKind.LINT)
        store.insert(
            OperationRecord(id = id, command = input, state = OperationState.RUNNING, owner = UUID.randomUUID()),
        )
        coordinator().use { service ->
            service.startWorker()
            assertThat(await(service, id).state).isEqualTo(OperationState.COMPLETED)
        }
        val old = UUID.randomUUID()
        store.insert(
            OperationRecord(
                id = old,
                command = input.copy(processorRevision = "old"),
                state = OperationState.RUNNING,
                owner = UUID.randomUUID(),
            ),
        )
        coordinator().use { service ->
            service.startWorker()
            assertThat(await(service, old).reason).isEqualTo("PROCESSOR_CHANGED")
        }
    }

    @Test
    fun `cancellation keeps CPU occupied until computation actually returns and preserves manual room`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executions = AtomicInteger()
        val input = command(OperationKind.LINT)
        val slow =
            mock(
                OperationEngine::class.java,
                org.mockito.stubbing.Answer { invocation ->
                    when (invocation.method.name) {
                        "revision" -> {
                            input.processorRevision
                        }

                        "execute" -> {
                            executions.incrementAndGet()
                            entered.countDown()
                            release.await(5, TimeUnit.SECONDS)
                            OperationOutput("CANCELLED")
                        }

                        else -> {
                            null
                        }
                    }
                },
            )
        val capacity = ProcessorCapacity(2)
        OperationCoordinator(
            store = store,
            engine = slow,
            settings = settings,
            capacity = capacity,
            limits = limits,
        ).use { service ->
            val id = UUID.randomUUID()
            service.start(id, input)
            service.startWorker()
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(
                (service.cancel(id) as OperationReply.Found).operation.state,
            ).isEqualTo(OperationState.CANCELLING)
            assertThat(capacity.acquire(backgroundWork = true)).isNull()
            val manual = capacity.acquire()
            assertThat(manual).isNotNull()
            manual?.close()
            release.countDown()
            assertThat(await(service, id).state).isEqualTo(OperationState.CANCELLED)
            assertThat(executions.get()).isEqualTo(1)
        }
    }

    @Test
    fun `clock skew is bounded and active records never expire`() {
        coordinator().use { service ->
            val id = UUID.randomUUID()
            val input = command(OperationKind.LINT).copy(submittedAt = Instant.now().plusSeconds(1))
            assertThat(service.start(id, input)).isInstanceOf(OperationReply.Found::class.java)
            assertThat(redis.getExpire("${settings.namespace}:$id")).isEqualTo(-1L)
            assertThat(service.start(UUID.randomUUID(), input.copy(submittedAt = Instant.now().plusSeconds(180))))
                .isEqualTo(OperationReply.Expired)
        }
    }

    @Test
    fun `lost Redis state cannot hide local CPU and an unavailable final write never repeats computation`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val count = AtomicInteger()
        val offline =
            java.util.concurrent.atomic
                .AtomicBoolean()
        val input = command(OperationKind.LINT)
        val slow =
            mock(
                OperationEngine::class.java,
                org.mockito.stubbing.Answer { invocation ->
                    when (invocation.method.name) {
                        "revision" -> {
                            input.processorRevision
                        }

                        "execute" -> {
                            count.incrementAndGet()
                            entered.countDown()
                            release.await(5, TimeUnit.SECONDS)
                            OperationOutput("LINTED")
                        }

                        else -> {
                            null
                        }
                    }
                },
            )
        val interrupted =
            object : OperationStore by store {
                override fun find(id: UUID): OperationRecord? {
                    if (offline.get()) throw OperationStoreUnavailable(IllegalStateException("test outage"))
                    return store.find(id)
                }
            }
        val capacity = ProcessorCapacity(2)
        OperationCoordinator(
            store = interrupted,
            engine = slow,
            settings = settings,
            capacity = capacity,
            limits = limits,
        ).use { service ->
            val id = UUID.randomUUID()
            service.start(id, input)
            service.startWorker()
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
            redis.delete("${settings.namespace}:$id")
            assertThat((service.find(id) as OperationReply.Found).operation.state).isEqualTo(OperationState.RUNNING)
            assertThat(
                (service.cancel(id) as OperationReply.Found).operation.state,
            ).isEqualTo(OperationState.CANCELLING)
            assertThat(capacity.acquire(backgroundWork = true)).isNull()
            offline.set(true)
            release.countDown()
            Thread.sleep(200)
            assertThat(count.get()).isEqualTo(1)
            offline.set(false)
            assertThat(await(service, id).state).isEqualTo(OperationState.CANCELLED)
            assertThat(count.get()).isEqualTo(1)
            assertThat(store.pending()).doesNotContain(id)
        }
    }

    private fun command(kind: OperationKind) =
        OperationCommand(
            kind = kind,
            version = "1.1",
            code = "println(1);",
            rules = emptyMap(),
            processorRevision = checkNotNull(engine.revision(kind)),
            submittedAt = Instant.now(),
        )

    private fun coordinator() =
        OperationCoordinator(
            store = store,
            engine = engine,
            settings = settings,
            capacity = ProcessorCapacity(limits.activeGlobal),
            limits = limits,
        )

    private fun await(
        service: OperationCoordinator,
        id: UUID,
    ): OperationRecord {
        val deadline = Instant.now().plus(Duration.ofSeconds(10))
        while (Instant.now() < deadline) {
            val record = (service.find(id) as? OperationReply.Found)?.operation
            if (record?.terminal == true) return record
            Thread.sleep(10)
        }
        error("Operation $id did not finish")
    }

    companion object {
        private val container = OperationRedis().also { it.start() }
        private val connection =
            LettuceConnectionFactory(container.host, container.firstMappedPort).also {
                it.afterPropertiesSet()
                it.start()
            }
        private val redis = StringRedisTemplate(connection)

        @JvmStatic @AfterAll
        fun stopRedis() {
            connection.destroy()
            container.stop()
        }
    }
}
