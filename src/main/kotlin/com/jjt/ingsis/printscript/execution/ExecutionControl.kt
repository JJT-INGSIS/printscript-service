package com.jjt.ingsis.printscript.execution

import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

// This exception is confined to the synchronous library adapter. The library has no typed stop result.
internal class ExecutionStopped(
    val terminal: ExecutionTerminal,
) : RuntimeException(null, null, false, false)

class ExecutionControl(
    limits: ExecutionLimits,
) {
    private val deadline = System.nanoTime() + Duration.ofSeconds(limits.totalSeconds).toNanos()
    private val stop = AtomicReference<ExecutionTerminal?>()

    fun request(
        outcome: ExecutionState,
        reason: String,
    ) {
        stop.compareAndSet(null, ExecutionTerminal(outcome, reason))
    }

    fun checkpoint() {
        if (System.nanoTime() >= deadline) request(ExecutionState.LIMIT_EXCEEDED, "execution-timeout")
        stop.get()?.let { throw ExecutionStopped(it) }
    }

    internal fun resultOr(result: ExecutionTerminal): ExecutionTerminal = stop.get() ?: result
}
