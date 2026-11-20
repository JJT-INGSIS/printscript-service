package com.jjt.ingsis.printscript.execution

import java.util.UUID

enum class ExecutionMode { TEST, INTERACTIVE }

enum class ExecutionState {
    RUNNING,
    WAITING_INPUT,
    CANCELLING,
    COMPLETED,
    PROGRAM_ERROR,
    INPUT_EXHAUSTED,
    LIMIT_EXCEEDED,
    CANCELLED,
    TECHNICAL_FAILURE,
    INTERRUPTED,
}

data class ExecutionCommand(
    val code: String,
    val version: String,
    val mode: ExecutionMode,
    val inputs: List<String> = emptyList(),
)

data class ExecutionTerminal(
    val outcome: ExecutionState,
    val reason: String,
    val diagnostics: List<ExecutionDiagnostic> = emptyList(),
    val unusedInputs: Int? = null,
)

data class ExecutionDiagnostic(
    val rule: String,
    val message: String,
    val line: Int,
    val column: Int,
)

data class ExecutionEvent(
    val executionId: UUID,
    val seq: Long,
    val type: String,
    val data: Map<String, Any>,
)

data class ExecutionDescriptor(
    val executionId: UUID,
    val mode: ExecutionMode,
    val state: ExecutionState,
    val limits: ExecutionLimits,
    val lastSeq: Long,
)

sealed interface ExecutionResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ExecutionResult<T>

    data class Rejected(
        val status: Rejection,
        val reason: String,
    ) : ExecutionResult<Nothing>
}

enum class Rejection { INVALID, MISSING, CONFLICT, CAPACITY, UNSUPPORTED }

internal fun <T> rejected(
    status: Rejection,
    reason: String,
): ExecutionResult<T> = ExecutionResult.Rejected(status, reason)
