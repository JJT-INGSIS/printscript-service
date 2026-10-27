package com.jjt.ingsis.printscript.execution

import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ExecutionSession(
    val id: UUID,
    private val command: ExecutionCommand,
    private val limits: ExecutionLimits,
    val journal: EventJournal,
) {
    private val state = AtomicReference(ExecutionState.RUNNING)
    private val control = ExecutionControl(limits)
    private val input =
        ExecutionInput(command = command, control = control, limits = limits, emit = ::emit, state = state)
    private val outputBytes = AtomicInteger()
    private val finishedAt = AtomicLong()
    val stopped = CountDownLatch(1)

    fun execute() {
        val result = PrintScriptExecution().run(command = command, control = control, input = input, output = ::output)
        finish(result)
    }

    @Synchronized
    private fun finish(result: ExecutionTerminal) {
        val terminal = control.resultOr(result)
        state.set(terminal.outcome)
        val data =
            mapOf<String, Any>("outcome" to terminal.outcome, "reason" to terminal.reason) +
                (if (terminal.diagnostics.isEmpty()) emptyMap() else mapOf("diagnostics" to terminal.diagnostics)) +
                (terminal.unusedInputs?.let { mapOf("unusedInputs" to it) }).orEmpty()
        journal.finish(data)
        finishedAt.set(System.nanoTime())
        stopped.countDown()
    }

    fun started() = emit("started", mapOf("mode" to command.mode))

    private fun output(text: String) {
        if (outputBytes.addAndGet(text.utf8Size()) > limits.outputBytes) {
            control.request(ExecutionState.LIMIT_EXCEEDED, "output-limit-exceeded")
            control.checkpoint()
        }
        emit("output", mapOf("text" to text))
    }

    private fun emit(
        type: String,
        data: Map<String, Any>,
    ) {
        control.checkpoint()
        if (!journal.append(type, data)) {
            control.request(ExecutionState.LIMIT_EXCEEDED, "event-buffer-exceeded")
            control.checkpoint()
        }
    }

    fun descriptor(): ExecutionDescriptor =
        ExecutionDescriptor(
            executionId = id,
            mode = command.mode,
            state = state.get(),
            limits = limits,
            lastSeq = journal.snapshot().lastSeq,
        )

    @Synchronized
    fun cancel(
        outcome: ExecutionState = ExecutionState.CANCELLED,
        reason: String = "actor-cancelled",
    ): ExecutionState {
        if (finishedAt.get() == 0L && state.get() in ACTIVE_STATES) {
            control.request(outcome, reason)
            state.compareAndSet(ExecutionState.RUNNING, ExecutionState.CANCELLING)
            state.compareAndSet(ExecutionState.WAITING_INPUT, ExecutionState.CANCELLING)
        }
        return state.get()
    }

    fun accept(
        requestId: UUID,
        value: String,
    ): ExecutionResult<Unit> =
        try {
            input.accept(requestId, value)
        } catch (_: ExecutionStopped) {
            rejected(Rejection.CONFLICT, "execution-stopping")
        }

    fun maintain(): Boolean {
        val ended = finishedAt.get()
        val disconnected = journal.snapshot()
        if (ended !=
            0L
        ) {
            return System.nanoTime() - ended >= Duration.ofSeconds(limits.terminalRetentionSeconds).toNanos()
        }
        if (!disconnected.connected &&
            System.nanoTime() - disconnected.disconnectedAt >= Duration.ofSeconds(limits.reconnectSeconds).toNanos()
        ) {
            cancel(ExecutionState.CANCELLED, "consumer-disconnected")
        }
        return false
    }

    companion object {
        val ACTIVE_STATES = setOf(ExecutionState.RUNNING, ExecutionState.WAITING_INPUT, ExecutionState.CANCELLING)
    }
}
