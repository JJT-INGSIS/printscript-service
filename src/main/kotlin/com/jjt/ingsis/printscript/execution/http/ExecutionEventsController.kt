package com.jjt.ingsis.printscript.execution.http

import com.jjt.ingsis.printscript.execution.ExecutionRegistry
import com.jjt.ingsis.printscript.execution.ExecutionSession
import com.jjt.ingsis.printscript.execution.ExecutionState
import com.jjt.ingsis.printscript.execution.Rejection
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.IOException
import java.io.OutputStream
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

@RestController
class ExecutionEventsController(
    private val registry: ExecutionRegistry,
    private val codec: ExecutionEventCodec,
) {
    @GetMapping("/executions/{id}/events", produces = ["application/x-ndjson"])
    fun events(
        @PathVariable id: String,
        @RequestParam(defaultValue = "0") after: String,
        response: HttpServletResponse,
    ): ResponseEntity<StreamingResponseBody> {
        val cursor = after.toLongOrNull() ?: invalidRequest()
        val session =
            registry.find(ExecutionRequests.uuid(id))
                ?: throw ExecutionHttpFailure(Rejection.MISSING, "execution-not-found")
        session.journal.connect(cursor).value()
        val body =
            StreamingResponseBody { output ->
                try {
                    stream(session = session, after = cursor, output = output, flush = response::flushBuffer)
                } catch (_: IOException) {
                    // A disconnected consumer has the contract's reconnect window; this is not completion.
                } finally {
                    session.journal.disconnect()
                }
            }
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType("application/x-ndjson"))
            .header("Cache-Control", "no-store")
            .header("X-Accel-Buffering", "no")
            .body(body)
    }

    private fun stream(
        session: ExecutionSession,
        after: Long,
        output: OutputStream,
        flush: () -> Unit,
    ) {
        val cursor = AtomicLong(after)
        val heartbeat = AtomicLong(System.nanoTime())
        while (true) {
            val event = session.journal.next(cursor.get())
            if (event != null) {
                output.write(codec.encode(event))
                output.write('\n'.code)
                // Spring 7 wraps this OutputStream with a non-flushing stream; commit each event explicitly.
                flush()
                session.journal.delivered(event.seq)
                cursor.set(event.seq)
                if (event.type == "terminal") return
            } else {
                if (session.journal.snapshot().terminal != null) return
                heartbeat(session, heartbeat)
                session.journal.awaitChange()
            }
        }
    }

    private fun heartbeat(
        session: ExecutionSession,
        last: AtomicLong,
    ) {
        if (System.nanoTime() - last.get() >= Duration.ofSeconds(HEARTBEAT_SECONDS).toNanos()) {
            if (!session.journal.append(
                    "heartbeat",
                    emptyMap(),
                )
            ) {
                session.cancel(ExecutionState.LIMIT_EXCEEDED, "event-buffer-exceeded")
            }
            last.set(System.nanoTime())
        }
    }

    companion object {
        private const val HEARTBEAT_SECONDS = 10L
    }
}
