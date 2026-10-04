package com.jjt.ingsis.printscript.diagnostic

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import printscript.ast.DeclaredType
import printscript.ast.expression.BinaryOperator
import printscript.ast.expression.UnaryOperator
import printscript.interpreter.SemanticError
import printscript.lexer.SourceReadingError
import printscript.model.source.SourcePosition
import printscript.model.source.SourceSpan
import printscript.source.SourceReadError
import printscript.statement.ParseError
import printscript.token.LexicalError
import printscript.token.Token
import printscript.token.TokenReadError
import printscript.token.TokenType
import printscript.v1.interpreter.PrintScriptV1SemanticError
import printscript.v1.lexer.PrintScriptV1LexicalError
import printscript.v1.token.PrintScriptV1TokenType

class DiagnosticsTest {
    private val span =
        SourceSpan(
            start = SourcePosition(line = 3, column = 7, offset = 20),
            end = SourcePosition(line = 4, column = 2, offset = 31),
        )

    private val parseErrors: List<Pair<ParseError, Diagnostic>> =
        listOf(
            ParseError.TokenRead(LexicalError.UnexpectedCharacter('@', span)) to
                at(DiagnosticRule.UNEXPECTED_CHARACTER, "el carácter '@' no pertenece al lenguaje"),
            ParseError.TokenRead(PrintScriptV1LexicalError.UnterminatedString('"', span)) to
                at(DiagnosticRule.UNTERMINATED_STRING, "falta cerrar el texto abierto con \""),
            ParseError.TokenRead(PrintScriptV1LexicalError.InvalidNumber("1.2.3", span)) to
                at(DiagnosticRule.INVALID_NUMBER, "'1.2.3' no es un número válido"),
            ParseError.UnexpectedToken(
                expected = setOf(PrintScriptV1TokenType.SEMICOLON),
                actual = Token(PrintScriptV1TokenType.EOF, "", span),
            ) to at(DiagnosticRule.UNEXPECTED_TOKEN, "se esperaba ';' pero se encontró el final del código"),
            ParseError.UnexpectedToken(
                expected =
                    setOf(
                        PrintScriptV1TokenType.NUMBER_LITERAL,
                        PrintScriptV1TokenType.IDENTIFIER,
                        PrintScriptV1TokenType.LEFT_PAREN,
                    ),
                actual = Token(PrintScriptV1TokenType.SEMICOLON, ";", span),
            ) to
                at(
                    DiagnosticRule.UNEXPECTED_TOKEN,
                    "se esperaba '(' o un identificador o un número pero se encontró ';'",
                ),
            ParseError.InvalidLiteral(Token(PrintScriptV1TokenType.NUMBER_LITERAL, "1e", span)) to
                at(DiagnosticRule.INVALID_LITERAL, "el literal '1e' no es válido"),
        )

    private val semanticErrors: List<Pair<SemanticError, Diagnostic>> =
        listOf(
            PrintScriptV1SemanticError.UndeclaredVariable("total", span) to
                at(DiagnosticRule.UNDECLARED_VARIABLE, "la variable 'total' no fue declarada"),
            PrintScriptV1SemanticError.UninitializedVariable("total", span) to
                at(DiagnosticRule.UNINITIALIZED_VARIABLE, "la variable 'total' se usa sin haber recibido un valor"),
            PrintScriptV1SemanticError.AlreadyDeclaredVariable("total", span) to
                at(DiagnosticRule.ALREADY_DECLARED_VARIABLE, "la variable 'total' ya fue declarada"),
            PrintScriptV1SemanticError.ConstantReassignment("limit", span) to
                at(DiagnosticRule.CONSTANT_REASSIGNMENT, "la constante 'limit' no puede reasignarse"),
            PrintScriptV1SemanticError.TypeMismatch(
                name = "total",
                expected = DeclaredType.NUMBER,
                actual = DeclaredType.STRING,
                span = span,
            ) to at(DiagnosticRule.TYPE_MISMATCH, "'total' es de tipo number y se le intentó asignar un string"),
            PrintScriptV1SemanticError.InvalidBinaryOperands(
                operator = BinaryOperator.SUBTRACT,
                left = DeclaredType.STRING,
                right = DeclaredType.BOOLEAN,
                span = span,
            ) to
                at(
                    DiagnosticRule.INVALID_BINARY_OPERANDS,
                    "el operador '-' no se puede aplicar entre string y boolean",
                ),
            PrintScriptV1SemanticError.InvalidUnaryOperand(UnaryOperator.MINUS, DeclaredType.STRING, span) to
                at(DiagnosticRule.INVALID_UNARY_OPERAND, "el operador '-' no se puede aplicar a un string"),
            PrintScriptV1SemanticError.InvalidIfCondition("total", DeclaredType.NUMBER, span) to
                at(
                    DiagnosticRule.INVALID_IF_CONDITION,
                    "la condición 'total' es de tipo number y debe ser boolean",
                ),
            PrintScriptV1SemanticError.InvalidInputPrompt(DeclaredType.NUMBER, span) to
                at(
                    DiagnosticRule.INVALID_INPUT_PROMPT,
                    "el mensaje de readInput es de tipo number y debe ser string",
                ),
            PrintScriptV1SemanticError.InvalidEnvironmentVariableName(DeclaredType.BOOLEAN, span) to
                at(
                    DiagnosticRule.INVALID_ENVIRONMENT_VARIABLE_NAME,
                    "el nombre recibido por readEnv es de tipo boolean y debe ser string",
                ),
            SemanticError.UnsupportedStatement(span) to
                at(DiagnosticRule.UNSUPPORTED_STATEMENT, "esta sentencia no está soportada en esta versión"),
            SemanticError.UnsupportedExpression(span) to
                at(DiagnosticRule.UNSUPPORTED_EXPRESSION, "esta expresión no está soportada en esta versión"),
        )

    private val executionErrors: List<PrintScriptV1SemanticError> =
        listOf(
            PrintScriptV1SemanticError.DivisionByZero(span),
            PrintScriptV1SemanticError.UnsupportedBinaryOperator(BinaryOperator.DIVIDE, span),
            PrintScriptV1SemanticError.InputUnavailable(span),
            PrintScriptV1SemanticError.InvalidInputValue(DeclaredType.NUMBER, span),
            PrintScriptV1SemanticError.EnvironmentVariableNotFound("HOME", span),
            PrintScriptV1SemanticError.InvalidEnvironmentVariableValue("PORT", DeclaredType.NUMBER, span),
        )

    @TestFactory
    fun `describes every lexical and syntax error of the library`(): List<DynamicTest> =
        parseErrors.map { (error, expected) ->
            dynamicTest(expected.message) { assertThat(error.toDiagnostic()).isEqualTo(expected) }
        }

    @TestFactory
    fun `describes every semantic error of the library`(): List<DynamicTest> =
        semanticErrors.map { (error, expected) ->
            dynamicTest(expected.rule.name) { assertThat(error.toDiagnostic()).isEqualTo(expected) }
        }

    @Test
    fun `gives every semantic rule to exactly one error`() {
        assertThat(semanticErrors.map { (_, expected) -> expected.rule }).doesNotHaveDuplicates()
    }

    @TestFactory
    fun `does not catalogue errors that only running the code can cause`(): List<DynamicTest> =
        executionErrors.map { error ->
            dynamicTest(error.javaClass.simpleName) {
                assertThat(error.toDiagnostic())
                    .isEqualTo(
                        at(
                            DiagnosticRule.UNKNOWN,
                            "la validación informó un error que solo debería aparecer al ejecutar",
                        ),
                    )
            }
        }

    @Test
    fun `keeps the position of errors it does not know`() {
        val unknownSyntaxError =
            object : ParseError {
                override val span = this@DiagnosticsTest.span
            }
        val unknownReadError =
            object : TokenReadError {
                override val span = this@DiagnosticsTest.span
            }
        val unknownSemanticError =
            object : SemanticError {
                override val span = this@DiagnosticsTest.span
            }

        assertThat(unknownSyntaxError.toDiagnostic())
            .isEqualTo(at(DiagnosticRule.UNKNOWN, "error sintáctico desconocido"))
        assertThat(ParseError.TokenRead(unknownReadError).toDiagnostic())
            .isEqualTo(at(DiagnosticRule.UNKNOWN, "error desconocido al leer el código"))
        assertThat(unknownSemanticError.toDiagnostic())
            .isEqualTo(at(DiagnosticRule.UNKNOWN, "error semántico desconocido"))
    }

    @Test
    fun `names a token type it does not know by its own description`() {
        val unknownTokenType =
            object : TokenType {
                override fun toString(): String = "ARROW"
            }
        val error =
            ParseError.UnexpectedToken(
                expected = setOf(unknownTokenType),
                actual = Token(PrintScriptV1TokenType.IDENTIFIER, "a", span),
            )

        assertThat(error.toDiagnostic().message).isEqualTo("se esperaba ARROW pero se encontró 'a'")
    }

    @Test
    fun `treats a source that could not be read as a technical failure, not as invalid code`() {
        val error = ParseError.TokenRead(SourceReadingError(SourceReadError.InvalidInputStreamEncoding, span))

        assertThatIllegalStateException().isThrownBy { error.toDiagnostic() }
    }

    private fun at(
        rule: DiagnosticRule,
        message: String,
    ): Diagnostic =
        Diagnostic(
            rule = rule,
            message = message,
            line = 3,
            column = 7,
        )
}
