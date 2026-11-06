package com.jjt.ingsis.printscript.operations

import com.jjt.ingsis.printscript.diagnostic.Diagnostic
import com.jjt.ingsis.printscript.formatting.FormattingOutcome
import com.jjt.ingsis.printscript.formatting.FormattingService
import com.jjt.ingsis.printscript.language.LanguageVersion
import com.jjt.ingsis.printscript.linting.LintingOutcome
import com.jjt.ingsis.printscript.linting.LintingService
import com.jjt.ingsis.printscript.processor.ProcessorIdentity
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class OperationEngine(
    private val linting: LintingService,
    private val formatting: FormattingService,
    private val identity: ProcessorIdentity,
) {
    private val json = JsonMapper.builder().build()
    private val rulesType = object : TypeReference<Map<String, JsonNode>>() {}

    fun revision(kind: OperationKind): String? =
        when (kind) {
            OperationKind.LINT -> identity.linting
            OperationKind.FORMAT -> identity.formatting
        }

    fun execute(
        command: OperationCommand,
        keepRunning: () -> Boolean,
    ): OperationOutput {
        val version = LanguageVersion.fromLabel(command.version)
        val rules = json.readValue(json.writeValueAsString(command.rules), rulesType)
        return when (command.kind) {
            OperationKind.LINT -> {
                lint(
                    linting.lint(version = version, code = command.code, rules = rules, keepRunning = keepRunning),
                )
            }

            OperationKind.FORMAT -> {
                format(
                    formatting.format(version = version, code = command.code, rules = rules, keepRunning = keepRunning),
                )
            }
        }
    }

    private fun diagnostic(value: Diagnostic): OperationDiagnostic =
        OperationDiagnostic(rule = value.rule.name, message = value.message, line = value.line, column = value.column)

    private fun lint(result: LintingOutcome): OperationOutput =
        when (result) {
            is LintingOutcome.Analyzed -> {
                OperationOutput(
                    "LINTED",
                    diagnostics =
                        result.diagnostics.map {
                            OperationDiagnostic(
                                rule = it.rule,
                                message = it.message,
                                line = it.line,
                                column = it.column,
                            )
                        },
                )
            }

            is LintingOutcome.InvalidCode -> {
                OperationOutput("INVALID_CODE", diagnostics = listOf(diagnostic(result.diagnostic)))
            }

            is LintingOutcome.InvalidRules -> {
                OperationOutput("INVALID_RULES", violations = result.violations)
            }

            LintingOutcome.CodeTooLarge -> {
                OperationOutput("CODE_TOO_LARGE")
            }

            LintingOutcome.DiagnosticsTooLarge -> {
                OperationOutput("DIAGNOSTICS_TOO_LARGE")
            }

            LintingOutcome.Cancelled -> {
                OperationOutput("CANCELLED")
            }
        }

    private fun format(result: FormattingOutcome): OperationOutput =
        when (result) {
            is FormattingOutcome.Formatted -> {
                OperationOutput("FORMATTED", code = result.code)
            }

            is FormattingOutcome.Unformattable -> {
                OperationOutput(
                    "INVALID_CODE",
                    diagnostics = listOf(diagnostic(result.diagnostic)),
                )
            }

            is FormattingOutcome.InvalidRules -> {
                OperationOutput("INVALID_RULES", violations = result.violations)
            }

            FormattingOutcome.CodeTooLarge -> {
                OperationOutput("CODE_TOO_LARGE")
            }

            FormattingOutcome.OutputTooLarge -> {
                OperationOutput("OUTPUT_TOO_LARGE")
            }

            FormattingOutcome.Cancelled -> {
                OperationOutput("CANCELLED")
            }
        }
}
