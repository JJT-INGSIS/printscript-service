package com.jjt.ingsis.printscript.formatting

import com.jjt.ingsis.printscript.language.LanguageVersion
import printscript.v1.configuration.ConfigurationCheck
import printscript.v1.configuration.ConfigurationRuleType
import printscript.v1.configuration.ConfigurationRuleViolation
import printscript.v1.configuration.ConfigurationRuleViolationReason
import tools.jackson.databind.JsonNode

internal class FormattingRuleCheck(
    private val version: LanguageVersion,
    private val maximumCount: Int,
) {
    fun violations(
        rules: Map<String, JsonNode>,
        configuration: String,
    ): List<RuleViolation> {
        val reported = libraryViolations(configuration)
        val reportedRules = reported.map { it.rule }.toSet()
        return reported + aboveMaximum(rules).filter { it.rule !in reportedRules }
    }

    private fun libraryViolations(configuration: String): List<RuleViolation> =
        when (val check = PrintScriptFormatters.check(version, configuration)) {
            ConfigurationCheck.Valid -> {
                emptyList()
            }

            is ConfigurationCheck.InvalidRules -> {
                check.violations.map { RuleViolation(it.rule, reasonFor(it)) }
            }

            is ConfigurationCheck.MalformedDocument -> {
                error(
                    "The service built an invalid JSON object: ${check.reason}",
                )
            }
        }

    private fun aboveMaximum(rules: Map<String, JsonNode>): List<RuleViolation> =
        PrintScriptFormatters
            .rules(version)
            .filter { it.type == ConfigurationRuleType.INTEGER && rules[it.id].isAbove(maximumCount) }
            .map { RuleViolation(it.id, "out-of-range") }

    private fun reasonFor(violation: ConfigurationRuleViolation): String =
        when (violation.reason) {
            ConfigurationRuleViolationReason.UNKNOWN_RULE -> unknownReason(violation.rule)
            ConfigurationRuleViolationReason.WRONG_TYPE -> "wrong-type"
            ConfigurationRuleViolationReason.OUT_OF_RANGE -> "out-of-range"
            ConfigurationRuleViolationReason.UNKNOWN_CHOICE -> "unknown-choice"
            ConfigurationRuleViolationReason.CONFLICTING_RULES -> "conflicting-rules"
        }

    private fun unknownReason(rule: String): String =
        if (LanguageVersion.entries.any { other -> PrintScriptFormatters.rules(other).any { it.id == rule } }) {
            "unavailable-in-version"
        } else {
            "unknown-rule"
        }

    private fun JsonNode?.isAbove(maximum: Int): Boolean =
        this != null && isIntegralNumber && bigIntegerValue() > maximum.toBigInteger()
}
