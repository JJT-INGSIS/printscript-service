package com.jjt.ingsis.printscript.formatting.http

import com.jjt.ingsis.printscript.formatting.FormattingLimits
import com.jjt.ingsis.printscript.formatting.FormattingService
import com.jjt.ingsis.printscript.language.LanguageVersion
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/formatting")
class FormattingController(
    private val formatting: FormattingService,
    private val limits: FormattingLimits,
) {
    @GetMapping("/rules")
    fun catalog(
        @RequestParam version: String,
    ): FormattingCatalogResponse {
        val rules = formatting.catalog(LanguageVersion.fromLabel(version))
        return FormattingCatalogResponse(version, rules.map { FormattingResponses.describe(it, limits.maximumCount) })
    }

    @PostMapping("/rules/check")
    fun check(
        @RequestBody request: FormattingRulesCheckRequest,
    ): RulesCheckResponse =
        RulesCheckResponse(
            formatting.check(LanguageVersion.fromLabel(request.version), request.rules.withExplicitNulls()),
        )

    @PostMapping
    fun format(
        @RequestBody request: FormattingRequest,
    ): ResponseEntity<Any> =
        FormattingResponses.of(
            formatting.format(
                version = LanguageVersion.fromLabel(request.version),
                code = request.code,
                rules = request.rules.withExplicitNulls(),
            ),
        )
}
