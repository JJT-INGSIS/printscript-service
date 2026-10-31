package com.jjt.ingsis.printscript.processor

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ProcessorController(
    private val identity: ProcessorIdentity,
) {
    @GetMapping("/processor", produces = ["application/json"])
    fun processor(): Map<String, Any?> = identity.manifest()
}
