package com.jjt.ingsis.printscript.execution

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ExecutionRegistry(
    private val limits: ExecutionLimits,
    private val sizeOf: (ExecutionEvent) -> Int,
) : AutoCloseable {
    private val sessions = ConcurrentHashMap<UUID, ExecutionSession>()
    private val capacity = Semaphore(limits.activeGlobal)
    private val workers = Executors.newFixedThreadPool(limits.activeGlobal)
    private val clock = Executors.newSingleThreadScheduledExecutor()
    private val closed = AtomicBoolean()

    init {
        clock.scheduleWithFixedDelay({ sessions.entries.removeIf { it.value.maintain() } }, 1, 1, TimeUnit.SECONDS)
    }

    @Synchronized
    fun start(command: ExecutionCommand): ExecutionResult<ExecutionDescriptor> {
        if (command.version !in setOf("1.0", "1.1")) return rejected(Rejection.UNSUPPORTED, "unsupported-version")
        if (command.code.utf8Size() > limits.codeBytes || command.inputs.any { it.utf8Size() > limits.inputBytes }) {
            return rejected(Rejection.INVALID, "request-too-large")
        }
        if (closed.get() || !capacity.tryAcquire()) return rejected(Rejection.CAPACITY, "execution-capacity-exceeded")
        val id = UUID.randomUUID()
        val session =
            ExecutionSession(id = id, command = command, limits = limits, journal = EventJournal(id, limits, sizeOf))
        sessions[id] = session
        session.started()
        workers.execute {
            try {
                session.execute()
            } finally {
                capacity.release()
            }
        }
        return ExecutionResult.Success(session.descriptor())
    }

    fun find(id: UUID): ExecutionSession? = sessions[id]

    @Synchronized
    override fun close() {
        closed.set(true)
        sessions.values.forEach { it.cancel(ExecutionState.INTERRUPTED, "interpreter-restarted") }
        workers.shutdown()
        workers.awaitTermination(limits.totalSeconds + 1, TimeUnit.SECONDS)
        clock.shutdownNow()
    }
}
