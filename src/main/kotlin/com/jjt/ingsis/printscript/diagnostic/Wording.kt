package com.jjt.ingsis.printscript.diagnostic

import printscript.ast.DeclaredType
import printscript.ast.expression.BinaryOperator
import printscript.ast.expression.UnaryOperator
import printscript.token.Token
import printscript.token.TokenType
import printscript.v1.lexer.PrintScriptV11LexerFactory
import printscript.v1.token.PrintScriptV1TokenType

internal object Wording {
    private const val END_OF_CODE = "el final del código"

    private val language = PrintScriptV11LexerFactory.defaultConfiguration()

    private val tokenDescriptions: Map<TokenType, String> =
        language.keywordTokenTypesByLexeme.map { (lexeme, tokenType) -> tokenType to "'$lexeme'" }.toMap() +
            language.symbolTokenTypesByCharacter.map { (symbol, tokenType) -> tokenType to "'$symbol'" } +
            mapOf(
                PrintScriptV1TokenType.IDENTIFIER to "un identificador",
                PrintScriptV1TokenType.NUMBER_LITERAL to "un número",
                PrintScriptV1TokenType.STRING_LITERAL to "un texto",
                PrintScriptV1TokenType.EOF to END_OF_CODE,
            )

    fun anyOf(expected: Set<TokenType>): String =
        expected
            .map { tokenType -> tokenDescriptions[tokenType] ?: tokenType.toString() }
            .sorted()
            .joinToString(separator = " o ")

    fun found(token: Token): String =
        if (token.type == PrintScriptV1TokenType.EOF) {
            END_OF_CODE
        } else {
            "'${token.lexeme}'"
        }

    fun describe(type: DeclaredType): String =
        when (type) {
            DeclaredType.NUMBER -> "number"
            DeclaredType.STRING -> "string"
            DeclaredType.BOOLEAN -> "boolean"
        }

    fun describe(operator: BinaryOperator): String =
        when (operator) {
            BinaryOperator.ADD -> "+"
            BinaryOperator.SUBTRACT -> "-"
            BinaryOperator.MULTIPLY -> "*"
            BinaryOperator.DIVIDE -> "/"
        }

    fun describe(operator: UnaryOperator): String =
        when (operator) {
            UnaryOperator.PLUS -> "+"
            UnaryOperator.MINUS -> "-"
        }
}
