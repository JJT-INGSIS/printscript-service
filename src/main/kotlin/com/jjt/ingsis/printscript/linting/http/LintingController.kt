package com.jjt.ingsis.printscript.linting.http

import com.jjt.ingsis.printscript.formatting.http.withExplicitNulls
import com.jjt.ingsis.printscript.language.LanguageVersion
import com.jjt.ingsis.printscript.linting.LintingOutcome
import com.jjt.ingsis.printscript.linting.LintingService
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.net.URI

data class LintingRequest(
    val version: String,
    val code: String,
    val rules: Map<String, JsonNode?>,
)

data class LintRulesRequest(
    val version: String,
    val rules: Map<String, JsonNode?>,
)

@RestController
@RequestMapping("/linting")
class LintingController(
    private val service: LintingService,
) {
    @GetMapping("/rules")
    fun catalog(
        @RequestParam version: String,
    ): Any =
        mapOf(
            "version" to version,
            "defaults" to service.defaults(LanguageVersion.fromLabel(version)),
            "rules" to
                service.catalog(LanguageVersion.fromLabel(version)).map {
                    mapOf(
                        "id" to it.id,
                        "type" to it.type.name.lowercase(java.util.Locale.ROOT),
                        "acceptsNull" to it.acceptsNull,
                        "choices" to it.choices,
                        "conflictsWith" to it.conflictsWith,
                    )
                },
        )

    @PostMapping("/rules/check")
    fun check(
        @RequestBody request: LintRulesRequest,
    ): Any {
        val violations = service.check(LanguageVersion.fromLabel(request.version), request.rules.withExplicitNulls())
        return mapOf("valid" to violations.isEmpty(), "violations" to violations)
    }

    @PostMapping
    fun lint(
        @RequestBody request: LintingRequest,
    ): ResponseEntity<Any> =
        when (
            val result =
                service.lint(
                    LanguageVersion.fromLabel(request.version),
                    request.code,
                    request.rules.withExplicitNulls(),
                )
        ) {
            is LintingOutcome.Analyzed -> {
                ResponseEntity.ok(
                    mapOf(
                        "analyzed" to true,
                        "compliant" to result.diagnostics.isEmpty(),
                        "diagnostics" to result.diagnostics,
                    ),
                )
            }

            is LintingOutcome.InvalidCode -> {
                ResponseEntity.ok(
                    mapOf(
                        "analyzed" to false,
                        "compliant" to null,
                        "diagnostics" to listOf(result.diagnostic),
                    ),
                )
            }

            is LintingOutcome.InvalidRules -> {
                problem(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "invalid-linting-rules",
                    result.violations,
                )
            }

            LintingOutcome.CodeTooLarge -> {
                problem(HttpStatus.BAD_REQUEST, "request-too-large")
            }

            LintingOutcome.DiagnosticsTooLarge -> {
                problem(HttpStatus.UNPROCESSABLE_CONTENT, "lint-diagnostics-too-large")
            }
        }

    private fun problem(
        status: HttpStatus,
        reason: String,
        violations: Any? = null,
    ): ResponseEntity<Any> {
        val body =
            ProblemDetail.forStatus(status).apply {
                type = URI.create("urn:printscript:problem:$reason")
                title = reason
                if (violations != null) setProperty("violations", violations)
            }
        return ResponseEntity.status(status).body(body)
    }
}
