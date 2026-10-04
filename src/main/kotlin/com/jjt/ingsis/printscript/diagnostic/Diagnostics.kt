package com.jjt.ingsis.printscript.diagnostic

import printscript.interpreter.SemanticError
import printscript.lexer.SourceReadingError
import printscript.model.source.SourceSpan
import printscript.statement.ParseError
import printscript.token.LexicalError
import printscript.token.TokenReadError
import printscript.v1.interpreter.PrintScriptV1SemanticError
import printscript.v1.lexer.PrintScriptV1LexicalError

private typealias Description = Pair<DiagnosticRule, String>

fun ParseError.toDiagnostic(): Diagnostic = describe().at(span)

fun SemanticError.toDiagnostic(): Diagnostic = describe().at(span)

private fun ParseError.describe(): Description =
    when (this) {
        is ParseError.TokenRead -> {
            this.error.describe()
        }

        is ParseError.UnexpectedToken -> {
            DiagnosticRule.UNEXPECTED_TOKEN to
                "se esperaba ${Wording.anyOf(expected)} pero se encontró ${Wording.found(actual)}"
        }

        is ParseError.InvalidLiteral -> {
            DiagnosticRule.INVALID_LITERAL to "el literal '${token.lexeme}' no es válido"
        }

        else -> {
            DiagnosticRule.UNKNOWN to "error sintáctico desconocido"
        }
    }

private fun TokenReadError.describe(): Description =
    when (this) {
        is LexicalError.UnexpectedCharacter -> {
            DiagnosticRule.UNEXPECTED_CHARACTER to "el carácter '$character' no pertenece al lenguaje"
        }

        is PrintScriptV1LexicalError.UnterminatedString -> {
            DiagnosticRule.UNTERMINATED_STRING to "falta cerrar el texto abierto con $openingQuote"
        }

        is PrintScriptV1LexicalError.InvalidNumber -> {
            DiagnosticRule.INVALID_NUMBER to "'$lexeme' no es un número válido"
        }

        is SourceReadingError -> {
            error("The source code could not be read: $sourceError")
        }

        else -> {
            DiagnosticRule.UNKNOWN to "error desconocido al leer el código"
        }
    }

private fun SemanticError.describe(): Description =
    when (this) {
        is PrintScriptV1SemanticError -> {
            describeLanguageError()
        }

        is SemanticError.UnsupportedStatement -> {
            DiagnosticRule.UNSUPPORTED_STATEMENT to "esta sentencia no está soportada en esta versión"
        }

        is SemanticError.UnsupportedExpression -> {
            DiagnosticRule.UNSUPPORTED_EXPRESSION to "esta expresión no está soportada en esta versión"
        }

        else -> {
            DiagnosticRule.UNKNOWN to "error semántico desconocido"
        }
    }

private fun PrintScriptV1SemanticError.describeLanguageError(): Description =
    when (this) {
        is PrintScriptV1SemanticError.UndeclaredVariable -> {
            DiagnosticRule.UNDECLARED_VARIABLE to "la variable '$name' no fue declarada"
        }

        is PrintScriptV1SemanticError.UninitializedVariable -> {
            DiagnosticRule.UNINITIALIZED_VARIABLE to "la variable '$name' se usa sin haber recibido un valor"
        }

        is PrintScriptV1SemanticError.AlreadyDeclaredVariable -> {
            DiagnosticRule.ALREADY_DECLARED_VARIABLE to "la variable '$name' ya fue declarada"
        }

        is PrintScriptV1SemanticError.ConstantReassignment -> {
            DiagnosticRule.CONSTANT_REASSIGNMENT to "la constante '$name' no puede reasignarse"
        }

        is PrintScriptV1SemanticError.TypeMismatch -> {
            DiagnosticRule.TYPE_MISMATCH to
                "'$name' es de tipo ${Wording.describe(expected)} " +
                "y se le intentó asignar un ${Wording.describe(actual)}"
        }

        is PrintScriptV1SemanticError.InvalidBinaryOperands -> {
            DiagnosticRule.INVALID_BINARY_OPERANDS to
                "el operador '${Wording.describe(operator)}' no se puede aplicar entre " +
                "${Wording.describe(left)} y ${Wording.describe(right)}"
        }

        is PrintScriptV1SemanticError.InvalidUnaryOperand -> {
            DiagnosticRule.INVALID_UNARY_OPERAND to
                "el operador '${Wording.describe(operator)}' no se puede aplicar a un ${Wording.describe(operand)}"
        }

        is PrintScriptV1SemanticError.InvalidIfCondition -> {
            DiagnosticRule.INVALID_IF_CONDITION to
                "la condición '$name' es de tipo ${Wording.describe(actual)} y debe ser boolean"
        }

        is PrintScriptV1SemanticError.InvalidInputPrompt -> {
            DiagnosticRule.INVALID_INPUT_PROMPT to
                "el mensaje de readInput es de tipo ${Wording.describe(actual)} y debe ser string"
        }

        is PrintScriptV1SemanticError.InvalidEnvironmentVariableName -> {
            DiagnosticRule.INVALID_ENVIRONMENT_VARIABLE_NAME to
                "el nombre recibido por readEnv es de tipo ${Wording.describe(actual)} y debe ser string"
        }

        is PrintScriptV1SemanticError.DivisionByZero,
        is PrintScriptV1SemanticError.UnsupportedBinaryOperator,
        is PrintScriptV1SemanticError.InputUnavailable,
        is PrintScriptV1SemanticError.InvalidInputValue,
        is PrintScriptV1SemanticError.EnvironmentVariableNotFound,
        is PrintScriptV1SemanticError.InvalidEnvironmentVariableValue,
        -> {
            DiagnosticRule.UNKNOWN to "la validación informó un error que solo debería aparecer al ejecutar"
        }
    }

private fun Description.at(span: SourceSpan): Diagnostic =
    Diagnostic(
        rule = first,
        message = second,
        line = span.start.line,
        column = span.start.column,
    )
