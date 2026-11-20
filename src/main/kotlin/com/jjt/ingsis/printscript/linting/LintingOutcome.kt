package com.jjt.ingsis.printscript.linting

import com.jjt.ingsis.printscript.diagnostic.Diagnostic
import com.jjt.ingsis.printscript.formatting.RuleViolation

data class LintFinding(
    val rule: String,
    val message: String,
    val line: Int,
    val column: Int,
)

sealed interface LintingOutcome {
    data object Cancelled : LintingOutcome

    data class Analyzed(
        val diagnostics: List<LintFinding>,
    ) : LintingOutcome

    data class InvalidCode(
        val diagnostic: Diagnostic,
    ) : LintingOutcome

    data class InvalidRules(
        val violations: List<RuleViolation>,
    ) : LintingOutcome

    data object CodeTooLarge : LintingOutcome

    data object DiagnosticsTooLarge : LintingOutcome
}
