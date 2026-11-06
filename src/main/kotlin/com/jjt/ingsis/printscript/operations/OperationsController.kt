package com.jjt.ingsis.printscript.operations

import com.jjt.ingsis.printscript.execution.ExecutionLimits
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/operations")
@ConditionalOnProperty("operations.enabled", havingValue = "true")
class OperationsController(
    private val operations: OperationCoordinator,
    private val limits: ExecutionLimits,
) {
    @PutMapping("/{id}")
    fun start(
        @PathVariable id: UUID,
        @RequestBody command: OperationCommand,
        @RequestHeader("Execution-Active-Limit", required = false) expectedLimit: Int?,
    ): ResponseEntity<*> =
        if (expectedLimit != null && expectedLimit != limits.activeGlobal) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("reason" to "CAPACITY_MISMATCH"))
        } else {
            response(operations.start(id, command))
        }

    @GetMapping("/{id}")
    fun find(
        @PathVariable id: UUID,
    ): ResponseEntity<*> = response(operations.find(id))

    @PostMapping("/{id}/cancel")
    fun cancel(
        @PathVariable id: UUID,
    ): ResponseEntity<*> = response(operations.cancel(id))

    @ExceptionHandler(OperationStoreUnavailable::class)
    fun unavailable(): ResponseEntity<*> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            mapOf("reason" to "OPERATION_STORAGE_UNAVAILABLE"),
        )

    private fun response(reply: OperationReply): ResponseEntity<*> =
        when (reply) {
            is OperationReply.Found -> {
                ResponseEntity
                    .ok()
                    .header(
                        "Execution-Active-Limit",
                        limits.activeGlobal.toString(),
                    ).body(reply.operation)
            }

            OperationReply.Missing -> {
                ResponseEntity.notFound().build<Any>()
            }

            OperationReply.Conflict -> {
                ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("reason" to "OPERATION_CONFLICT"))
            }

            OperationReply.Expired -> {
                ResponseEntity.status(HttpStatus.GONE).body(mapOf("reason" to "ADMISSION_EXPIRED"))
            }

            OperationReply.Invalid -> {
                ResponseEntity.badRequest().body(mapOf("reason" to "INVALID_OPERATION"))
            }

            OperationReply.Full -> {
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapOf("reason" to "OPERATION_CAPACITY"))
            }
        }
}
