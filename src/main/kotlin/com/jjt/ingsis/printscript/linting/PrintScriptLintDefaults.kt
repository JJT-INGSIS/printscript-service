package com.jjt.ingsis.printscript.linting

import com.jjt.ingsis.printscript.language.LanguageVersion
import printscript.v1.linter.PrintScriptArgumentAcceptancePolicy
import printscript.v1.linter.PrintScriptV11LinterFactory
import printscript.v1.linter.PrintScriptV1LinterFactory
import printscript.v1.linter.PrintScriptV1RuleConfiguration
import java.util.Locale

// This adapter encodes the published library's selected defaults; it does not choose defaults.
internal object PrintScriptLintDefaults {
    fun selection(version: LanguageVersion): Map<String, Any> =
        when (version) {
            LanguageVersion.V1_0 -> PrintScriptV1LinterFactory.defaultConfiguration().rules
            LanguageVersion.V1_1 -> PrintScriptV11LinterFactory.defaultConfiguration().rules
        }.associate { rule ->
            when (rule) {
                is PrintScriptV1RuleConfiguration.IdentifierNaming -> {
                    "identifier_format" to
                        rule.convention.name
                            .lowercase(Locale.ROOT)
                            .replace('_', ' ')
                }

                is PrintScriptV1RuleConfiguration.PrintlnArgument -> {
                    "mandatory-variable-or-literal-in-println" to restricted(rule.acceptance)
                }

                is PrintScriptV1RuleConfiguration.ReadInputArgument -> {
                    "mandatory-variable-or-literal-in-readInput" to restricted(rule.acceptance)
                }
            }
        }

    private fun restricted(policy: PrintScriptArgumentAcceptancePolicy): Boolean {
        check(policy == PrintScriptArgumentAcceptancePolicy.variableOrLiteral()) {
            "Published default policy cannot be represented by the lint configuration contract"
        }
        return true
    }
}
