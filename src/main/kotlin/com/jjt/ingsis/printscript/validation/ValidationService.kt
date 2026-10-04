package com.jjt.ingsis.printscript.validation

import com.jjt.ingsis.printscript.diagnostic.toDiagnostic
import com.jjt.ingsis.printscript.language.LanguageVersion
import com.jjt.ingsis.printscript.language.PrintScriptToolchains
import org.springframework.stereotype.Service
import printscript.v1.validation.ValidationResult

@Service
class ValidationService {
    fun validate(
        code: String,
        version: LanguageVersion,
    ): ValidationResponse {
        val diagnostics =
            when (val result = PrintScriptToolchains.forVersion(version).validate(code)) {
                ValidationResult.Success -> emptyList()
                is ValidationResult.ParseFailure -> listOf(result.error.toDiagnostic())
                is ValidationResult.SemanticFailure -> listOf(result.error.toDiagnostic())
            }

        return ValidationResponse(diagnostics)
    }
}
