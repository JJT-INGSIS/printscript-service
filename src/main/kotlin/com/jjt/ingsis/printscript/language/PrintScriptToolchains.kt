package com.jjt.ingsis.printscript.language

import printscript.v1.lexer.PrintScriptV11LexerFactory
import printscript.v1.lexer.PrintScriptV1LexerFactory
import printscript.v1.parser.PrintScriptV11ParserFactory
import printscript.v1.parser.PrintScriptV1ParserFactory
import printscript.v1.validation.PrintScriptV11ValidatorFactory
import printscript.v1.validation.PrintScriptV1ValidatorFactory

object PrintScriptToolchains {
    private val version1_0 =
        PrintScriptToolchain(
            lexer = PrintScriptV1LexerFactory.create(),
            parser = PrintScriptV1ParserFactory.create(),
            validator = PrintScriptV1ValidatorFactory.create(),
        )

    private val version1_1 =
        PrintScriptToolchain(
            lexer = PrintScriptV11LexerFactory.create(),
            parser = PrintScriptV11ParserFactory.create(),
            validator = PrintScriptV11ValidatorFactory.create(),
        )

    fun forVersion(version: LanguageVersion): PrintScriptToolchain =
        when (version) {
            LanguageVersion.V1_0 -> version1_0
            LanguageVersion.V1_1 -> version1_1
        }
}
