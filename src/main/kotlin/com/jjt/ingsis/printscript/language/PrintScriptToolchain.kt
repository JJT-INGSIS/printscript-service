package com.jjt.ingsis.printscript.language

import printscript.lexer.Lexer
import printscript.parser.Parser
import printscript.source.SourceReaderFactory
import printscript.v1.validation.ValidationResult
import printscript.v1.validation.Validator

class PrintScriptToolchain(
    private val lexer: Lexer,
    private val parser: Parser,
    private val validator: Validator,
) {
    fun validate(code: String): ValidationResult {
        val tokens = lexer.tokenize(SourceReaderFactory.fromString(code))

        return validator.validate(parser.parse(tokens))
    }
}
