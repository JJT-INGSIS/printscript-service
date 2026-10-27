package com.jjt.ingsis.printscript.execution.http

import com.jjt.ingsis.printscript.execution.ExecutionDescriptor
import com.jjt.ingsis.printscript.execution.ExecutionRegistry
import com.jjt.ingsis.printscript.execution.ExecutionSession
import com.jjt.ingsis.printscript.execution.Rejection
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.net.URI

@RestController
@RequestMapping("/executions")
class ExecutionController(
    private val registry: ExecutionRegistry,
) {
    @PostMapping(consumes = ["application/json"], produces = ["application/json"])
    fun start(
        @RequestBody body: JsonNode,
    ): ResponseEntity<ExecutionDescriptor> {
        val descriptor = registry.start(ExecutionRequests.start(body)).value()
        return ResponseEntity.created(URI.create("/executions/${descriptor.executionId}")).body(descriptor)
    }

    @GetMapping("/{id}", produces = ["application/json"])
    fun status(
        @PathVariable id: String,
    ): ExecutionDescriptor = session(id).descriptor()

    @PostMapping("/{id}/inputs", consumes = ["application/json"], produces = ["application/json"])
    fun input(
        @PathVariable id: String,
        @RequestBody body: JsonNode,
    ): Map<String, Any> {
        val (requestId, value) = ExecutionRequests.input(body)
        session(id).accept(requestId, value).value()
        return mapOf("requestId" to requestId, "accepted" to true)
    }

    @PostMapping("/{id}/cancel", consumes = ["application/json"], produces = ["application/json"])
    fun cancel(
        @PathVariable id: String,
        @RequestBody body: JsonNode,
    ): ResponseEntity<Map<String, Any>> {
        ExecutionRequests.empty(body)
        return ResponseEntity.accepted().body(mapOf("state" to session(id).cancel()))
    }

    private fun session(id: String): ExecutionSession =
        registry.find(ExecutionRequests.uuid(id))
            ?: throw ExecutionHttpFailure(Rejection.MISSING, "execution-not-found")
}
