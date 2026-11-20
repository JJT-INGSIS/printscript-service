package com.jjt.ingsis.printscript.execution

import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal data class InputRequest(
    val id: UUID,
    val value: CompletableFuture<String> = CompletableFuture(),
)

class ExecutionInput(
    private val command: ExecutionCommand,
    private val control: ExecutionControl,
    private val limits: ExecutionLimits,
    private val emit: (String, Map<String, Any>) -> Unit,
    private val state: AtomicReference<ExecutionState>,
) {
    private val cursor = AtomicInteger()
    private val pending = AtomicReference<InputRequest?>()
    private val accepted = AtomicReference<Map<UUID, String>>(emptyMap())
    private val monitor = Any()

    fun read(prompt: String): String {
        control.checkpoint()
        return when (command.mode) {
            ExecutionMode.TEST -> predefined()
            ExecutionMode.INTERACTIVE -> interactive(prompt)
        }
    }

    private fun predefined(): String {
        val index = cursor.getAndIncrement()
        if (index >=
            command.inputs.size
        ) {
            throw ExecutionStopped(ExecutionTerminal(ExecutionState.INPUT_EXHAUSTED, "test-input-exhausted"))
        }
        return command.inputs[index]
    }

    private fun interactive(prompt: String): String {
        val request = InputRequest(UUID.randomUUID())
        synchronized(monitor) {
            pending.set(request)
            state.set(ExecutionState.WAITING_INPUT)
            emit("input-required", mapOf("requestId" to request.id, "prompt" to prompt))
        }
        val deadline = System.nanoTime() + Duration.ofSeconds(limits.inputWaitSeconds).toNanos()
        return try {
            awaitValue(request, deadline)
        } finally {
            pending.compareAndSet(request, null)
        }
    }

    private fun awaitValue(
        request: InputRequest,
        deadline: Long,
    ): String {
        while (true) {
            control.checkpoint()
            if (System.nanoTime() >=
                deadline
            ) {
                throw ExecutionStopped(ExecutionTerminal(ExecutionState.LIMIT_EXCEEDED, "input-wait-timeout"))
            }
            try {
                return request.value.get(INPUT_POLL_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                // A bounded wait lets cancellation stop the actual interpreter, not just the HTTP stream.
            }
        }
    }

    fun accept(
        id: UUID,
        value: String,
    ): ExecutionResult<Unit> =
        synchronized(monitor) {
            val prior = accepted.get()[id]
            when {
                value.utf8Size() > limits.inputBytes -> {
                    rejected(Rejection.INVALID, "input-too-large")
                }

                prior != null -> {
                    if (prior ==
                        value
                    ) {
                        ExecutionResult.Success(Unit)
                    } else {
                        rejected(Rejection.CONFLICT, "input-conflict")
                    }
                }

                command.mode != ExecutionMode.INTERACTIVE || pending.get()?.id != id ||
                    state.get() != ExecutionState.WAITING_INPUT -> {
                    rejected(Rejection.CONFLICT, "input-not-requested")
                }

                else -> {
                    val request = checkNotNull(pending.get())
                    emit("input-accepted", mapOf("requestId" to id))
                    accepted.set(accepted.get() + (id to value))
                    pending.set(null)
                    state.set(ExecutionState.RUNNING)
                    request.value.complete(value)
                    ExecutionResult.Success(Unit)
                }
            }
        }

    fun unused(): Int = (command.inputs.size - cursor.get()).coerceAtLeast(0)

    companion object {
        private const val INPUT_POLL_MILLIS = 50L
    }
}
