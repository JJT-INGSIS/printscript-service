package com.jjt.ingsis.printscript.operations

import com.jjt.ingsis.printscript.execution.ExecutionLimits
import com.jjt.ingsis.printscript.execution.utf8Size
import com.jjt.ingsis.printscript.processor.ProcessorCapacity
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val WORKER_POLL_MS = 100L

private class InFlight(
    val record: OperationRecord,
) {
    val cancelled = AtomicBoolean()
    val completed = AtomicReference<OperationRecord?>()
}

class OperationCoordinator(
    private val store: OperationStore,
    private val engine: OperationEngine,
    private val settings: OperationsSettings,
    private val capacity: ProcessorCapacity,
    private val limits: ExecutionLimits,
) : AutoCloseable {
    private val boot = UUID.randomUUID()
    private val local = AtomicReference<InFlight?>()
    private val closed = AtomicBoolean()
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val log = LoggerFactory.getLogger(OperationCoordinator::class.java)

    fun startWorker() {
        worker.scheduleWithFixedDelay(::tick, 0, WORKER_POLL_MS, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun find(id: UUID): OperationReply {
        // A lost Redis write must not turn a still-running local computation into an apparent absence.
        val running = local.get()?.takeIf { it.record.id == id }
        return (store.find(id) ?: running?.record)?.let(OperationReply::Found) ?: OperationReply.Missing
    }

    @Synchronized
    fun start(
        id: UUID,
        command: OperationCommand,
    ): OperationReply {
        val previous = (find(id) as? OperationReply.Found)?.operation
        if (previous != null) {
            return if (previous.command == command || previous.command == null) {
                OperationReply.Found(previous)
            } else {
                OperationReply.Conflict
            }
        }
        val age = Duration.between(command.submittedAt, Instant.now())
        if (age < settings.admissionWindow.negated() || age > settings.admissionWindow) return OperationReply.Expired
        if (command.version !in setOf("1.0", "1.1") ||
            command.code.utf8Size() > limits.codeBytes
        ) {
            return OperationReply.Invalid
        }
        if (command.processorRevision != engine.revision(command.kind)) return OperationReply.Conflict
        val record = OperationRecord(id, command, OperationState.PENDING)
        if (store.insert(record)) return OperationReply.Found(record)
        return store.find(id)?.let { if (it.command == command) OperationReply.Found(it) else OperationReply.Conflict }
            ?: OperationReply.Full
    }

    @Synchronized
    fun cancel(id: UUID): OperationReply {
        val running = local.get()?.takeIf { it.record.id == id }
        running?.cancelled?.set(true)
        val before = store.find(id)
        return when {
            before == null && running != null -> {
                OperationReply.Found(running.record.copy(state = OperationState.CANCELLING))
            }

            before == null -> {
                val fence =
                    OperationRecord(id = id, command = null, state = OperationState.CANCELLED, reason = "START_FENCED")
                if (store.insert(fence)) OperationReply.Found(fence) else cancel(id)
            }

            before.terminal -> {
                OperationReply.Found(before)
            }

            else -> {
                val state =
                    if (before.state ==
                        OperationState.PENDING
                    ) {
                        OperationState.CANCELLED
                    } else {
                        OperationState.CANCELLING
                    }
                val after = before.copy(state = state)
                if (store.replace(before, after)) OperationReply.Found(after) else cancel(id)
            }
        }
    }

    // Scheduler/I/O boundary. Storage failures keep the in-flight result locally for durable finalization.
    private fun tick() {
        if (closed.get()) return
        try {
            val inflight = local.get()
            if (inflight != null) {
                finalize(inflight)
                return
            }
            val permit = capacity.acquire(backgroundWork = true) ?: return
            permit.use {
                val work = claim() ?: return
                compute(work)
            }
        } catch (_: OperationStoreUnavailable) {
            log.warn("Durable operation storage unavailable; retaining pending work")
        }
    }

    @Synchronized
    private fun claim(): InFlight? {
        if (closed.get()) return null
        val before =
            store.pending().firstNotNullOfOrNull { store.find(it)?.takeUnless(OperationRecord::terminal) }
                ?: return null
        val command = before.command ?: return null
        // Deployment contract: one process per namespace, stop-first. A predecessor has exited before this boot.
        if (before.state == OperationState.CANCELLING && before.owner != boot) {
            store.replace(before, before.copy(state = OperationState.CANCELLED, reason = "PREDECESSOR_STOPPED"))
            return null
        }
        if (command.processorRevision != engine.revision(command.kind)) {
            store.replace(before, before.copy(state = OperationState.FAILED, reason = "PROCESSOR_CHANGED"))
            return null
        }
        val claimed = before.copy(state = OperationState.RUNNING, owner = boot)
        return if (store.replace(before, claimed)) InFlight(claimed).also(local::set) else null
    }

    // Unexpected library failures are contained at the worker boundary; never log user input.
    @Suppress("TooGenericExceptionCaught")
    private fun compute(work: InFlight) {
        val result =
            try {
                val started = System.nanoTime()
                val output =
                    engine.execute(checkNotNull(work.record.command)) {
                        !closed.get() && !work.cancelled.get() &&
                            System.nanoTime() - started < Duration.ofSeconds(limits.totalSeconds).toNanos()
                    }
                if (work.cancelled.get() || closed.get()) {
                    work.record.copy(state = OperationState.CANCELLED, reason = "COMPUTATION_STOPPED")
                } else if (output.outcome == "CANCELLED") {
                    work.record.copy(state = OperationState.FAILED, reason = "PROCESSING_TIMEOUT")
                } else {
                    work.record.copy(state = OperationState.COMPLETED, output = output)
                }
            } catch (_: RuntimeException) {
                work.record.copy(state = OperationState.FAILED, reason = "PROCESSING_FAILED")
            }
        work.completed.set(result)
        finalize(work)
    }

    @Synchronized
    private fun finalize(work: InFlight) {
        val completed = work.completed.get() ?: return
        val before = store.find(work.record.id)
        // No capacity is released based on cancellation alone: compute() has actually returned here.
        val after =
            if (work.cancelled.get()) {
                completed.copy(
                    state = OperationState.CANCELLED,
                    output = null,
                )
            } else {
                completed
            }
        val saved = if (before == null) store.insert(after) else store.replace(before, after)
        if (saved) local.compareAndSet(work, null)
    }

    override fun close() {
        closed.set(true)
        local.get()?.cancelled?.set(true)
        worker.shutdown()
        worker.awaitTermination(limits.totalSeconds + 1, TimeUnit.SECONDS)
    }
}
