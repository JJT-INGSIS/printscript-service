package com.jjt.ingsis.printscript.validation

import com.jjt.ingsis.printscript.language.LanguageVersion
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ValidationController(
    private val validationService: ValidationService,
) {
    @PostMapping("/validate")
    fun validate(
        @RequestBody request: ValidationRequest,
    ): ValidationResponse =
        validationService.validate(
            code = request.code,
            version = LanguageVersion.fromLabel(request.version),
        )
}
