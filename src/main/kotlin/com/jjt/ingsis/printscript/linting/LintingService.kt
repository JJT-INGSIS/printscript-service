package com.jjt.ingsis.printscript.linting

import com.jjt.ingsis.printscript.diagnostic.toDiagnostic
import com.jjt.ingsis.printscript.execution.utf8Size
import com.jjt.ingsis.printscript.formatting.RuleViolation
import com.jjt.ingsis.printscript.language.LanguageVersion
import com.jjt.ingsis.printscript.language.PrintScriptToolchains
import org.springframework.stereotype.Service
import printscript.linter.DiagnosticReadResult
import printscript.linter.DiagnosticSource
import printscript.v1.configuration.ConfigurationCheck
import printscript.v1.configuration.ConfigurationRule
import printscript.v1.linter.PrintScriptV11Diagnostic
import printscript.v1.linter.PrintScriptV1Diagnostic
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.Locale

@Service
class LintingService(
    private val limits: LintingLimits,
) {
    private val json = JsonMapper.builder().build()

    fun catalog(version: LanguageVersion): List<ConfigurationRule> = PrintScriptLinters.rules(version)

    fun check(
        version: LanguageVersion,
        rules: Map<String, JsonNode>,
    ): List<RuleViolation> =
        when (val checked = PrintScriptLinters.check(version, json.writeValueAsString(rules))) {
            ConfigurationCheck.Valid -> {
                emptyList()
            }

            is ConfigurationCheck.InvalidRules -> {
                checked.violations.map {
                    RuleViolation(
                        it.rule,
                        it.reason.name
                            .lowercase(Locale.ROOT)
                            .replace('_', '-'),
                    )
                }
            }

            is ConfigurationCheck.MalformedDocument -> {
                error("The service generated an invalid JSON object")
            }
        }

    fun lint(
        version: LanguageVersion,
        code: String,
        rules: Map<String, JsonNode>,
    ): LintingOutcome {
        if (code.utf8Size() > limits.codeBytes) return LintingOutcome.CodeTooLarge
        val violations = check(version, rules)
        if (violations.isNotEmpty()) return LintingOutcome.InvalidRules(violations)
        val source = PrintScriptToolchains.forVersion(version).statements(code)
        return collect(PrintScriptLinters.create(version, json.writeValueAsString(rules)).lint(source))
    }

    private fun collect(initial: DiagnosticSource): LintingOutcome {
        var source = initial
        val findings = mutableListOf<LintFinding>()
        while (true) {
            when (val next = source.nextDiagnostic()) {
                DiagnosticReadResult.EndOfInput -> {
                    return LintingOutcome.Analyzed(findings)
                }

                is DiagnosticReadResult.Failure -> {
                    return LintingOutcome.InvalidCode(next.error.toDiagnostic())
                }

                is DiagnosticReadResult.Success -> {
                    if (findings.size >= limits.diagnosticLimit) return LintingOutcome.DiagnosticsTooLarge
                    findings.add(finding(next.diagnostic))
                    source = next.remainingSource
                }
            }
        }
    }

    private fun finding(diagnostic: printscript.linter.Diagnostic): LintFinding {
        val description =
            when (diagnostic) {
                is PrintScriptV1Diagnostic.NamingConventionViolation -> {
                    "identifier_format" to "El identificador no cumple la convención configurada."
                }

                is PrintScriptV1Diagnostic.UnsupportedPrintlnArgument -> {
                    "mandatory-variable-or-literal-in-println" to "println requiere una variable o un literal."
                }

                is PrintScriptV11Diagnostic.UnsupportedReadInputArgument -> {
                    "mandatory-variable-or-literal-in-readInput" to "readInput requiere una variable o un literal."
                }

                else -> {
                    error("Unknown lint diagnostic")
                }
            }
        return LintFinding(
            rule = description.first,
            message = description.second,
            line = diagnostic.span.start.line,
            column = diagnostic.span.start.column,
        )
    }
}
