package com.jjt.ingsis.printscript.execution

import printscript.source.SourceChunkReadResult
import printscript.source.SourceReader
import printscript.statement.StatementReadResult
import printscript.statement.StatementSource
import printscript.token.TokenReadResult
import printscript.token.TokenSource

internal class ControlledReader(
    private val source: SourceReader,
    private val control: ExecutionControl,
) : SourceReader {
    override fun readChunk(): SourceChunkReadResult {
        control.checkpoint()
        return when (val result = source.readChunk()) {
            SourceChunkReadResult.EndOfInput -> {
                result
            }

            is SourceChunkReadResult.Failure -> {
                result.copy(
                    remainingReader = ControlledReader(result.remainingReader, control),
                )
            }

            is SourceChunkReadResult.Success -> {
                result.copy(
                    remainingReader = ControlledReader(result.remainingReader, control),
                )
            }
        }
    }
}

internal class ControlledTokens(
    private val source: TokenSource,
    private val control: ExecutionControl,
) : TokenSource {
    override fun nextToken(): TokenReadResult {
        control.checkpoint()
        return when (val result = source.nextToken()) {
            is TokenReadResult.Success -> {
                result.copy(
                    remainingSource = ControlledTokens(result.remainingSource, control),
                )
            }

            is TokenReadResult.Failure -> {
                result.copy(
                    remainingSource = ControlledTokens(result.remainingSource, control),
                )
            }
        }
    }
}

internal class ControlledStatements(
    private val source: StatementSource,
    private val control: ExecutionControl,
) : StatementSource {
    override fun nextStatement(): StatementReadResult {
        control.checkpoint()
        return when (val result = source.nextStatement()) {
            StatementReadResult.EndOfInput -> {
                result
            }

            is StatementReadResult.Failure -> {
                result
            }

            is StatementReadResult.Success -> {
                result.copy(
                    remainingSource = ControlledStatements(result.remainingSource, control),
                )
            }
        }
    }
}
