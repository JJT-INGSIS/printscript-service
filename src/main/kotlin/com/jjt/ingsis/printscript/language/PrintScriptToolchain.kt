package com.jjt.ingsis.printscript.language

import printscript.lexer.Lexer
import printscript.parser.Parser
import printscript.source.SourceReaderFactory
import printscript.token.TokenSource
import printscript.v1.validation.ValidationResult
import printscript.v1.validation.Validator

class PrintScriptToolchain(
    private val lexer: Lexer,
    private val parser: Parser,
    private val validator: Validator,
) {
    fun validate(code: String): ValidationResult = validator.validate(parser.parse(tokens(code)))

    fun tokens(code: String): TokenSource = lexer.tokenize(SourceReaderFactory.fromString(code))
}
