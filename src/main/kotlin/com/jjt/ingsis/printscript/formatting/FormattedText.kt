package com.jjt.ingsis.printscript.formatting

import com.jjt.ingsis.printscript.diagnostic.toDiagnostic
import com.jjt.ingsis.printscript.execution.utf8Size
import printscript.formatter.FormattedChunkReadResult
import printscript.formatter.FormattedSource
import printscript.formatter.FormattingError
import printscript.v1.formatter.WhitespaceSizeLimitExceeded

internal class FormattedText(
    private val outputBytes: Int,
) {
    fun collect(source: FormattedSource): FormattingOutcome = collect(source, StringBuilder(), 0)

    private tailrec fun collect(
        source: FormattedSource,
        text: StringBuilder,
        bytes: Long,
    ): FormattingOutcome =
        when (val chunk = source.nextFormattedChunk()) {
            FormattedChunkReadResult.EndOfInput -> {
                FormattingOutcome.Formatted(text.toString())
            }

            is FormattedChunkReadResult.Failure -> {
                failure(chunk.error)
            }

            is FormattedChunkReadResult.Success -> {
                val size = bytes + chunk.formattedText.utf8Size()
                if (size > outputBytes) {
                    FormattingOutcome.OutputTooLarge
                } else {
                    collect(chunk.remainingSource, text.append(chunk.formattedText), size)
                }
            }
        }

    private fun failure(error: FormattingError): FormattingOutcome =
        when (error) {
            is FormattingError.TokenReadFailure -> FormattingOutcome.Unformattable(error.tokenReadError.toDiagnostic())
            is WhitespaceSizeLimitExceeded -> FormattingOutcome.OutputTooLarge
            else -> error("The formatter reported an unknown error: $error")
        }
}
