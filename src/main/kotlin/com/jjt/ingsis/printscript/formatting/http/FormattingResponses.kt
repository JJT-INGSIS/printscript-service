package com.jjt.ingsis.printscript.formatting.http

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.jjt.ingsis.printscript.diagnostic.Diagnostic
import com.jjt.ingsis.printscript.formatting.FormattingOutcome
import com.jjt.ingsis.printscript.formatting.FormattingRuleDescriptions
import com.jjt.ingsis.printscript.formatting.RuleViolation
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import printscript.v1.configuration.ConfigurationRule
import printscript.v1.configuration.ConfigurationRuleType
import java.net.URI

data class FormattingCatalogResponse(
    val version: String,
    val rules: List<RuleDescription>,
)

data class RuleDescription(
    val id: String,
    val type: String,
    val default: Boolean?,
    val minimum: Int?,
    val maximum: Int?,
    val conflictsWith: List<String>,
    val description: String,
)

@JsonPropertyOrder("valid", "violations")
data class RulesCheckResponse(
    val violations: List<RuleViolation>,
) {
    val valid: Boolean
        get() = violations.isEmpty()
}

@JsonPropertyOrder("formatted", "code", "diagnostics")
data class FormattingResponse(
    val code: String?,
    val diagnostics: List<Diagnostic>,
) {
    val formatted: Boolean
        get() = code != null
}

internal object FormattingResponses {
    fun describe(
        rule: ConfigurationRule,
        maximumCount: Int,
    ): RuleDescription =
        when (rule.type) {
            ConfigurationRuleType.BOOLEAN -> {
                RuleDescription(
                    id = rule.id,
                    type = "boolean",
                    default = false,
                    minimum = null,
                    maximum = null,
                    conflictsWith = rule.conflictsWith.toList(),
                    description = FormattingRuleDescriptions.of(rule.id),
                )
            }

            ConfigurationRuleType.INTEGER -> {
                RuleDescription(
                    id = rule.id,
                    type = "integer",
                    default = null,
                    minimum = 0,
                    maximum = maximumCount,
                    conflictsWith = rule.conflictsWith.toList(),
                    description = FormattingRuleDescriptions.of(rule.id),
                )
            }

            ConfigurationRuleType.CHOICE -> {
                error("The formatter catalog has no choice rules: ${rule.id}")
            }
        }

    fun of(outcome: FormattingOutcome): ResponseEntity<Any> =
        when (outcome) {
            FormattingOutcome.Cancelled -> {
                problem(HttpStatus.CONFLICT, "operation-cancelled")
            }

            is FormattingOutcome.Formatted -> {
                ResponseEntity.ok(FormattingResponse(outcome.code, emptyList()))
            }

            is FormattingOutcome.Unformattable -> {
                ResponseEntity.ok(FormattingResponse(null, listOf(outcome.diagnostic)))
            }

            is FormattingOutcome.InvalidRules -> {
                problem(HttpStatus.UNPROCESSABLE_CONTENT, "invalid-formatting-rules", outcome.violations)
            }

            FormattingOutcome.CodeTooLarge -> {
                problem(HttpStatus.BAD_REQUEST, "request-too-large")
            }

            FormattingOutcome.OutputTooLarge -> {
                problem(HttpStatus.UNPROCESSABLE_CONTENT, "formatted-output-too-large")
            }
        }

    private fun problem(
        status: HttpStatus,
        reason: String,
        violations: List<RuleViolation>? = null,
    ): ResponseEntity<Any> {
        val problem =
            ProblemDetail.forStatus(status).apply {
                type = URI.create("urn:printscript:problem:$reason")
                title = reason
                detail = "The formatting request could not be completed."
                if (violations != null) setProperty("violations", violations)
            }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem)
    }
}
