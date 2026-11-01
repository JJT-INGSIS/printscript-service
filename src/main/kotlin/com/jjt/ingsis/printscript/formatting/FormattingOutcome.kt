package com.jjt.ingsis.printscript.formatting

import com.jjt.ingsis.printscript.diagnostic.Diagnostic

sealed interface FormattingOutcome {
    data class Formatted(
        val code: String,
    ) : FormattingOutcome

    data class Unformattable(
        val diagnostic: Diagnostic,
    ) : FormattingOutcome

    data class InvalidRules(
        val violations: List<RuleViolation>,
    ) : FormattingOutcome

    data object CodeTooLarge : FormattingOutcome

    data object OutputTooLarge : FormattingOutcome
}
