package com.jjt.ingsis.printscript.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

class ExecutionTest {
    private val mapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `published interpreter emits output before input and accepts empty input exactly once`() {
        registry().use { registry ->
            val session =
                start(
                    registry,
                    "println(\"antes\"); let a: string = readInput(\"Nombre?\"); println(a);",
                    ExecutionMode.INTERACTIVE,
                )
            waitFor { session.descriptor().state == ExecutionState.WAITING_INPUT }
            val events = session.journal.snapshot().events
            assertThat(events.map { it.type }).containsExactly("started", "output", "input-required")
            assertThat(events[1].data["text"]).isEqualTo("antes")
            val request = events.last().data["requestId"] as UUID
            assertThat(session.accept(request, "")).isInstanceOf(ExecutionResult.Success::class.java)
            assertThat(session.accept(request, "")).isInstanceOf(ExecutionResult.Success::class.java)
            assertThat(session.accept(request, "different")).isInstanceOf(ExecutionResult.Rejected::class.java)
            assertThat(session.stopped.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(session.descriptor().state).isEqualTo(ExecutionState.COMPLETED)
            assertThat(
                session.journal
                    .snapshot()
                    .events
                    .filter {
                        it.type == "output"
                    }.map { it.data["text"] },
            ).containsExactly("antes", "")
        }
    }

    @Test
    fun `separate environments and ordered predefined empty duplicate inputs`() {
        registry().use { registry ->
            val code = "let a: string = readInput(\"?\"); println(a); a = readInput(\"?\"); println(a);"
            val first = start(registry, code, inputs = listOf("", "ñ", "unused"))
            val second = start(registry, code, inputs = listOf("other", "other"))
            for (session in listOf(first, second)) assertThat(session.stopped.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(outputs(first)).containsExactly("", "ñ")
            assertThat(outputs(second)).containsExactly("other", "other")
            assertThat(
                first.journal
                    .snapshot()
                    .terminal
                    ?.data
                    ?.get("unusedInputs"),
            ).isEqualTo(1)
        }
    }

    @Test
    fun `input exhaustion and runtime diagnostics are not successful tests`() {
        registry().use { registry ->
            val exhausted = start(registry, "let a: string = readInput(\"?\");")
            val division = start(registry, "println(1 / 0);", version = "1.0")
            for (session in listOf(exhausted, division)) assertThat(session.stopped.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(exhausted.descriptor().state).isEqualTo(ExecutionState.INPUT_EXHAUSTED)
            assertThat(division.descriptor().state).isEqualTo(ExecutionState.PROGRAM_ERROR)
            assertThat(
                mapper.writeValueAsString(division.journal.snapshot().terminal),
            ).contains("DIVISION_BY_ZERO", "\"line\":1", "\"column\":")
        }
    }

    @Test
    fun `readEnv cannot access private process environment`() {
        registry().use { registry ->
            val session = start(registry, "let a: string = readEnv(\"PATH\"); println(a);")
            assertThat(session.stopped.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(session.descriptor().state).isEqualTo(ExecutionState.PROGRAM_ERROR)
            assertThat(outputs(session)).isEmpty()
            assertThat(
                mapper.writeValueAsString(session.journal.snapshot().terminal),
            ).contains("ENVIRONMENT_VARIABLE_NOT_FOUND")
        }
    }

    @Test
    fun `cancellation of input really exits interpreter and frees capacity`() {
        registry(ExecutionLimits(activeGlobal = 1)).use { registry ->
            val waiting = start(registry, "let a: string = readInput(\"?\");", ExecutionMode.INTERACTIVE)
            waitFor { waiting.descriptor().state == ExecutionState.WAITING_INPUT }
            assertThat(
                registry.start(ExecutionCommand("", "1.0", ExecutionMode.TEST)),
            ).isInstanceOf(ExecutionResult.Rejected::class.java)
            waiting.cancel()
            assertThat(waiting.stopped.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(waiting.descriptor().state).isEqualTo(ExecutionState.CANCELLED)
            waitFor { registry.start(ExecutionCommand("", "1.0", ExecutionMode.TEST)) is ExecutionResult.Success }
        }
    }

    @Test
    fun `cancel computation with no IO and nested statements stops the actual worker`() {
        registry().use { registry ->
            val code = "let a: number = 0; println(\"ready\");" + "a = a + 1;".repeat(50_000)
            val session = start(registry, code)
            waitFor { outputs(session).contains("ready") }
            session.cancel()
            assertThat(session.stopped.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(session.descriptor().state).isEqualTo(ExecutionState.CANCELLED)
        }
    }

    @Test
    fun `buffer and output limits terminate without blocking or losing terminal`() {
        registry(ExecutionLimits(bufferEvents = 2)).use { registry ->
            val session = start(registry, "println(1); println(2); println(3);")
            assertThat(session.stopped.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(
                session.journal
                    .snapshot()
                    .terminal
                    ?.data
                    ?.get("reason"),
            ).isEqualTo("event-buffer-exceeded")
            assertThat(session.journal.snapshot().events).hasSize(2)
        }
        registry(ExecutionLimits(outputBytes = 1)).use { registry ->
            val session = start(registry, "println(\"ñ\");")
            assertThat(session.stopped.await(2, TimeUnit.SECONDS)).isTrue()
            assertThat(
                session.journal
                    .snapshot()
                    .terminal
                    ?.data
                    ?.get("reason"),
            ).isEqualTo("output-limit-exceeded")
        }
    }

    @Test
    fun `shutdown interrupts active sessions and recreation does not recover old IDs`() {
        val registry = registry()
        val session = start(registry, "let a: string = readInput(\"?\");", ExecutionMode.INTERACTIVE)
        waitFor { session.descriptor().state == ExecutionState.WAITING_INPUT }
        registry.close()
        assertThat(session.stopped.count).isZero()
        assertThat(session.descriptor().state).isEqualTo(ExecutionState.INTERRUPTED)
        registry().use { assertThat(it.find(session.id)).isNull() }
    }

    private fun registry(limits: ExecutionLimits = ExecutionLimits()): ExecutionRegistry =
        ExecutionRegistry(limits) { mapper.writeValueAsBytes(it).size + 1 }

    private fun start(
        registry: ExecutionRegistry,
        code: String,
        mode: ExecutionMode = ExecutionMode.TEST,
        inputs: List<String> = emptyList(),
        version: String = "1.1",
    ): ExecutionSession {
        val result =
            registry.start(
                ExecutionCommand(code = code, version = version, mode = mode, inputs = inputs),
            ) as ExecutionResult.Success
        return checkNotNull(registry.find(result.value.executionId))
    }

    private fun outputs(session: ExecutionSession): List<Any?> =
        session.journal
            .snapshot()
            .events
            .filter {
                it.type ==
                    "output"
            }.map { it.data["text"] }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos()
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("Condition did not become true within two seconds")
    }
}
