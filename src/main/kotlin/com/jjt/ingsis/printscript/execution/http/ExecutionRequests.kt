package com.jjt.ingsis.printscript.execution.http

import com.jjt.ingsis.printscript.execution.ExecutionCommand
import com.jjt.ingsis.printscript.execution.ExecutionMode
import tools.jackson.databind.JsonNode
import java.util.UUID

internal object ExecutionRequests {
    fun start(body: JsonNode): ExecutionCommand {
        val mode =
            when (text(body, "mode")) {
                "TEST" -> ExecutionMode.TEST
                "INTERACTIVE" -> ExecutionMode.INTERACTIVE
                else -> invalidRequest()
            }
        val fields =
            if (mode ==
                ExecutionMode.TEST
            ) {
                setOf("code", "version", "mode", "inputs")
            } else {
                setOf("code", "version", "mode")
            }
        if (!body.isObject || body.properties().map { it.key }.toSet() != fields) invalidRequest()
        val inputs: List<String> =
            if (mode == ExecutionMode.TEST) {
                val values = body.path("inputs")
                if (!values.isArray || values.any { !it.isString }) invalidRequest()
                values.asSequence().map { it.asString() }.toList()
            } else {
                emptyList()
            }
        return ExecutionCommand(
            code = text(body, "code"),
            version = text(body, "version"),
            mode = mode,
            inputs = inputs,
        )
    }

    fun input(body: JsonNode): Pair<UUID, String> {
        if (!body.isObject || body.properties().map { it.key }.toSet() != setOf("requestId", "value")) invalidRequest()
        return uuid(text(body, "requestId")) to text(body, "value")
    }

    fun empty(body: JsonNode) {
        if (!body.isObject || !body.isEmpty) invalidRequest()
    }

    fun uuid(value: String): UUID {
        if (!value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) invalidRequest()
        return UUID.fromString(value)
    }

    private fun text(
        body: JsonNode,
        field: String,
    ): String {
        val value = body.path(field)
        if (!value.isString) invalidRequest()
        return value.asString()
    }
}
