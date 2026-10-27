package com.jjt.ingsis.printscript.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.TimeUnit

class ExecutionLifecycleTest {
    private val mapper = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `input wait total timeout and missing consumer stop actual interpreter`() {
        for ((limits, reason) in listOf(
            ExecutionLimits(inputWaitSeconds = 1) to "input-wait-timeout",
            ExecutionLimits(totalSeconds = 1) to "execution-timeout",
            ExecutionLimits(reconnectSeconds = 1) to "consumer-disconnected",
        )) {
            ExecutionRegistry(limits) { mapper.writeValueAsBytes(it).size + 1 }.use { registry ->
                val command = ExecutionCommand("let a: string = readInput(\"?\");", "1.1", ExecutionMode.INTERACTIVE)
                val result = registry.start(command) as ExecutionResult.Success
                val session = checkNotNull(registry.find(result.value.executionId))
                assertThat(session.stopped.await(4, TimeUnit.SECONDS)).isTrue()
                assertThat(
                    session.journal
                        .snapshot()
                        .terminal
                        ?.data
                        ?.get("reason"),
                ).isEqualTo(reason)
            }
        }
    }

    @Test
    fun `terminal data expires without pretending to persist executions`() {
        val limits = ExecutionLimits(terminalRetentionSeconds = 1)
        ExecutionRegistry(limits) { mapper.writeValueAsBytes(it).size + 1 }.use { registry ->
            val result = registry.start(ExecutionCommand("", "1.0", ExecutionMode.TEST)) as ExecutionResult.Success
            val session = checkNotNull(registry.find(result.value.executionId))
            assertThat(session.stopped.await(2, TimeUnit.SECONDS)).isTrue()
            Thread.sleep(2200)
            assertThat(registry.find(result.value.executionId)).isNull()
        }
    }
}
