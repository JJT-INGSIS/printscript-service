package com.jjt.ingsis.printscript.execution.http

import com.jjt.ingsis.printscript.execution.ExecutionEvent
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.SerializationFeature

@Component
class ExecutionEventCodec(
    mapper: ObjectMapper,
) {
    private val writer = mapper.writer().without(SerializationFeature.INDENT_OUTPUT)

    fun encode(event: ExecutionEvent): ByteArray = writer.writeValueAsBytes(event)
}
