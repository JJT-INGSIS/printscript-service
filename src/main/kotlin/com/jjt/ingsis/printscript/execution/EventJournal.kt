package com.jjt.ingsis.printscript.execution

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal data class JournalState(
    val events: List<ExecutionEvent> = emptyList(),
    val bytes: Int = 0,
    val lastSeq: Long = 0,
    val delivered: Long = 0,
    val connected: Boolean = false,
    val disconnectedAt: Long = System.nanoTime(),
    val terminal: ExecutionEvent? = null,
)

class EventJournal(
    private val id: UUID,
    private val limits: ExecutionLimits,
    private val sizeOf: (ExecutionEvent) -> Int,
) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val state = AtomicReference(JournalState())

    fun append(
        type: String,
        data: Map<String, Any>,
    ): Boolean =
        lock.withLock {
            val current = state.get()
            val event = ExecutionEvent(executionId = id, seq = current.lastSeq + 1, type = type, data = data)
            val retained = retainedFor(current, sizeOf(event))
            if (current.terminal != null || retained == null) return false
            state.set(
                retained.copy(
                    events = retained.events + event,
                    bytes = retained.bytes + sizeOf(event),
                    lastSeq = event.seq,
                ),
            )
            changed.signalAll()
            true
        }

    private tailrec fun retainedFor(
        current: JournalState,
        extra: Int,
    ): JournalState? {
        if (current.events.size < limits.bufferEvents && current.bytes + extra <= limits.bufferBytes) return current
        val oldest = current.events.firstOrNull() ?: return null
        if (oldest.seq > current.delivered) return null
        return retainedFor(current.copy(events = current.events.drop(1), bytes = current.bytes - sizeOf(oldest)), extra)
    }

    fun finish(data: Map<String, Any>) =
        lock.withLock {
            val current = state.get()
            if (current.terminal == null) {
                // Terminal has its own bounded control slot, never displaced by output backpressure.
                val terminal =
                    ExecutionEvent(executionId = id, seq = current.lastSeq + 1, type = "terminal", data = data)
                state.set(current.copy(lastSeq = terminal.seq, terminal = terminal))
                changed.signalAll()
            }
        }

    fun connect(after: Long): ExecutionResult<Unit> =
        lock.withLock {
            val current = state.get()
            when {
                after < 0 || after > current.lastSeq -> {
                    rejected(Rejection.INVALID, "invalid-cursor")
                }

                current.connected -> {
                    rejected(Rejection.CONFLICT, "execution-consumer-conflict")
                }

                after < (current.events.firstOrNull()?.seq ?: current.terminal?.seq ?: 1) - 1 -> {
                    rejected(Rejection.CONFLICT, "event-history-unavailable")
                }

                else -> {
                    state.set(current.copy(connected = true, delivered = maxOf(current.delivered, after)))
                    ExecutionResult.Success(Unit)
                }
            }
        }

    fun next(after: Long): ExecutionEvent? =
        lock.withLock {
            val current = state.get()
            current.events.firstOrNull { it.seq > after } ?: current.terminal?.takeIf { it.seq > after }
        }

    fun delivered(seq: Long) =
        lock.withLock {
            state.set(state.get().let { it.copy(delivered = maxOf(it.delivered, seq)) })
        }

    fun disconnect() =
        lock.withLock {
            state.set(state.get().copy(connected = false, disconnectedAt = System.nanoTime()))
        }

    fun awaitChange() = lock.withLock { changed.awaitNanos(POLL_NANOS) }

    internal fun snapshot(): JournalState = state.get()

    companion object {
        private const val POLL_NANOS = 50_000_000L
    }
}
