package com.jjt.ingsis.printscript.execution

import com.jjt.ingsis.printscript.diagnostic.Diagnostic
import com.jjt.ingsis.printscript.diagnostic.toDiagnostic
import printscript.interpreter.SemanticError
import printscript.v1.interpreter.PrintScriptV1SemanticError

internal fun SemanticError.toExecutionDiagnostic(): ExecutionDiagnostic {
    val rule =
        when (this) {
            is PrintScriptV1SemanticError.DivisionByZero -> {
                "DIVISION_BY_ZERO"
            }

            is PrintScriptV1SemanticError.InputUnavailable -> {
                "INPUT_UNAVAILABLE"
            }

            is PrintScriptV1SemanticError.InvalidInputValue -> {
                "INVALID_INPUT_VALUE"
            }

            is PrintScriptV1SemanticError.EnvironmentVariableNotFound -> {
                "ENVIRONMENT_VARIABLE_NOT_FOUND"
            }

            is PrintScriptV1SemanticError.InvalidEnvironmentVariableValue -> {
                "INVALID_ENVIRONMENT_VARIABLE_VALUE"
            }

            is PrintScriptV1SemanticError.UnsupportedBinaryOperator -> {
                "UNSUPPORTED_BINARY_OPERATOR"
            }

            else -> {
                return toDiagnostic().toExecutionDiagnostic()
            }
        }
    return ExecutionDiagnostic(
        rule = rule,
        message = "El programa no pudo completar la operación: $rule",
        line = span.start.line,
        column = span.start.column,
    )
}

internal fun Diagnostic.toExecutionDiagnostic(): ExecutionDiagnostic =
    ExecutionDiagnostic(rule = rule.name, message = message, line = line, column = column)
