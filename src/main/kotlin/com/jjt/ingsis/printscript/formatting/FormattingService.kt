package com.jjt.ingsis.printscript.formatting

import com.jjt.ingsis.printscript.execution.utf8Size
import com.jjt.ingsis.printscript.language.LanguageVersion
import com.jjt.ingsis.printscript.language.PrintScriptToolchains
import org.springframework.stereotype.Service
import printscript.v1.configuration.ConfigurationRule
import tools.jackson.databind.JsonNode

@Service
class FormattingService(
    private val limits: FormattingLimits,
) {
    fun catalog(version: LanguageVersion): List<ConfigurationRule> = PrintScriptFormatters.rules(version)

    fun check(
        version: LanguageVersion,
        rules: Map<String, JsonNode>,
    ): List<RuleViolation> = FormattingRuleCheck(version, limits.maximumCount).violations(rules, configurationOf(rules))

    fun format(
        version: LanguageVersion,
        code: String,
        rules: Map<String, JsonNode>,
    ): FormattingOutcome {
        val violations = check(version, rules)
        return when {
            violations.isNotEmpty() -> FormattingOutcome.InvalidRules(violations)
            code.utf8Size() > limits.codeBytes -> FormattingOutcome.CodeTooLarge
            else -> formatted(version, code, configurationOf(rules))
        }
    }

    private fun formatted(
        version: LanguageVersion,
        code: String,
        configuration: String,
    ): FormattingOutcome {
        val formatter = PrintScriptFormatters.create(version, configuration)
        val tokens = PrintScriptToolchains.forVersion(version).tokens(code)
        return FormattedText(limits.outputBytes).collect(formatter.format(tokens))
    }

    private fun configurationOf(rules: Map<String, JsonNode>): String =
        rules.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { "\"${it.key}\":${it.value}" }
}
