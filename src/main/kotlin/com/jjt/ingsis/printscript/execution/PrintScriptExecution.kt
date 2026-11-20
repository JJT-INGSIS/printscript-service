package com.jjt.ingsis.printscript.execution

import com.jjt.ingsis.printscript.diagnostic.toDiagnostic
import printscript.interpreter.InterpretationResult
import printscript.runtime.EnvironmentVariableProvider
import printscript.runtime.ProgramInput
import printscript.runtime.ProgramOutput
import printscript.source.SourceReaderFactory
import printscript.v1.interpreter.PrintScriptV11InterpreterFactory
import printscript.v1.interpreter.PrintScriptV1InterpreterFactory
import printscript.v1.lexer.PrintScriptV11LexerFactory
import printscript.v1.lexer.PrintScriptV1LexerFactory
import printscript.v1.parser.PrintScriptV11ParserFactory
import printscript.v1.parser.PrintScriptV1ParserFactory

class PrintScriptExecution {
    fun run(
        command: ExecutionCommand,
        control: ExecutionControl,
        input: ExecutionInput,
        output: (String) -> Unit,
    ): ExecutionTerminal =
        try {
            interpret(command = command, control = control, input = input, output = output)
        } catch (stop: ExecutionStopped) {
            stop.terminal
        } catch (_: StackOverflowError) {
            ExecutionTerminal(ExecutionState.LIMIT_EXCEEDED, "program-depth-exceeded")
        } catch (_: RuntimeException) {
            // The interpreter is a foreign synchronous I/O boundary. Never expose runtime internals or secrets.
            ExecutionTerminal(ExecutionState.TECHNICAL_FAILURE, "interpreter-failure")
        }

    private fun interpret(
        command: ExecutionCommand,
        control: ExecutionControl,
        input: ExecutionInput,
        output: (String) -> Unit,
    ): ExecutionTerminal {
        val version11 = command.version == "1.1"
        val lexer = if (version11) PrintScriptV11LexerFactory.create() else PrintScriptV1LexerFactory.create()
        val parser = if (version11) PrintScriptV11ParserFactory.create() else PrintScriptV1ParserFactory.create()
        val reader = ControlledReader(SourceReaderFactory.fromString(command.code), control)
        val source = ControlledStatements(parser.parse(ControlledTokens(lexer.tokenize(reader), control)), control)
        val writer =
            ProgramOutput { text ->
                control.checkpoint()
                output(text)
            }
        val checkpoints = listOf(ExecutionCheckpoint(control))
        val interpreter =
            if (version11) {
                PrintScriptV11InterpreterFactory.create(
                    output = writer,
                    input = ProgramInput(input::read),
                    environmentVariables =
                        EnvironmentVariableProvider {
                            null
                        },
                    additionalStatementExecutors = checkpoints,
                )
            } else {
                PrintScriptV1InterpreterFactory.create(writer, checkpoints)
            }
        return when (val result = interpreter.interpret(source)) {
            InterpretationResult.Success -> {
                ExecutionTerminal(
                    ExecutionState.COMPLETED,
                    "program-completed",
                    unusedInputs = input.unused(),
                )
            }

            is InterpretationResult.ParseFailure -> {
                programError(result.error.toDiagnostic().toExecutionDiagnostic())
            }

            is InterpretationResult.SemanticFailure -> {
                programError(result.error.toExecutionDiagnostic())
            }
        }
    }

    private fun programError(diagnostic: ExecutionDiagnostic): ExecutionTerminal =
        ExecutionTerminal(
            ExecutionState.PROGRAM_ERROR,
            "program-error",
            listOf(diagnostic.copy(message = diagnostic.message.take(MAX_DIAGNOSTIC_CHARACTERS))),
        )

    companion object {
        private const val MAX_DIAGNOSTIC_CHARACTERS = 1024
    }
}
