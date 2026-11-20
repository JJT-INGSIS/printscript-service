package com.jjt.ingsis.printscript.execution

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("execution")
data class ExecutionLimits(
    val activeGlobal: Int = 4,
    val totalSeconds: Long = 120,
    val inputWaitSeconds: Long = 30,
    val reconnectSeconds: Long = 15,
    val codeBytes: Int = 1_048_576,
    val inputBytes: Int = 8192,
    val outputBytes: Int = 1_048_576,
    val bufferEvents: Int = 256,
    val bufferBytes: Int = 262_144,
    val terminalRetentionSeconds: Long = 60,
) {
    init {
        require(listOf(activeGlobal, codeBytes, inputBytes, outputBytes, bufferEvents, bufferBytes).all { it > 0 })
        require(bufferBytes >= MIN_CONTROL_BUFFER_BYTES) { "The event buffer must fit the started control event" }
        require(listOf(totalSeconds, inputWaitSeconds, reconnectSeconds, terminalRetentionSeconds).all { it > 0 })
    }

    companion object {
        private const val MIN_CONTROL_BUFFER_BYTES = 512
    }
}

internal fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size
