package com.jjt.ingsis.printscript.validation

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.jjt.ingsis.printscript.diagnostic.Diagnostic

@JsonPropertyOrder("valid", "diagnostics")
data class ValidationResponse(
    val diagnostics: List<Diagnostic>,
) {
    val valid: Boolean
        get() = diagnostics.isEmpty()
}
