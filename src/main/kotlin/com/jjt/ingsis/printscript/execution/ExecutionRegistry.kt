package com.jjt.ingsis.printscript.execution

import com.jjt.ingsis.printscript.processor.ProcessorCapacity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ExecutionRegistry(
    private val limits: ExecutionLimits,
    private val capacity: ProcessorCapacity = ProcessorCapacity(limits.activeGlobal),
    private val sizeOf: (ExecutionEvent) -> Int,
) : AutoCloseable {
    private val sessions = ConcurrentHashMap<UUID, ExecutionSession>()
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
        if (closed.get()) return rejected(Rejection.CAPACITY, "execution-capacity-exhausted")
        val permit = capacity.acquire() ?: return rejected(Rejection.CAPACITY, "execution-capacity-exhausted")
        val id = UUID.randomUUID()
        val session =
            ExecutionSession(id = id, command = command, limits = limits, journal = EventJournal(id, limits, sizeOf))
        sessions[id] = session
        session.started()
        workers.execute {
            try {
                session.execute()
            } finally {
                permit.close()
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
