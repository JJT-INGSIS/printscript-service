package com.jjt.ingsis.printscript.formatting

import com.jjt.ingsis.printscript.diagnostic.DiagnosticRule
import com.jjt.ingsis.printscript.language.LanguageVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class FormattingServiceTest {
    private val mapper = JsonMapper.builder().build()
    private val service = FormattingService(FormattingLimits())
    private val program = "let a:number=1+2;println(a);let b : string = \"ñandú 🐧\";println(b);"

    @Test
    fun `without rules the original whitespace is kept exactly, including Unicode and CRLF`() {
        val code = "let a:number=1;\r\nprintln(\"ñandú 🐧\");"

        assertThat(format(code, "{}")).isEqualTo(FormattingOutcome.Formatted(code))
    }

    @Test
    fun `each rule changes only the whitespace it describes`() {
        assertThat(format(program, """{"enforce-spacing-around-equals":true}"""))
            .isEqualTo(formatted("let a:number = 1+2;println(a);let b : string = \"ñandú 🐧\";println(b);"))
        assertThat(format(program, """{"enforce-no-spacing-around-equals":true}"""))
            .isEqualTo(formatted("let a:number=1+2;println(a);let b : string=\"ñandú 🐧\";println(b);"))
        assertThat(format(program, """{"mandatory-space-surrounding-operations":true}"""))
            .isEqualTo(formatted("let a:number=1 + 2;println(a);let b : string = \"ñandú 🐧\";println(b);"))
        assertThat(format(program, """{"mandatory-line-break-after-statement":true}"""))
            .isEqualTo(formatted("let a:number=1+2;\nprintln(a);\nlet b : string = \"ñandú 🐧\";\nprintln(b);"))
        assertThat(format(program, """{"line-breaks-after-println":2}"""))
            .isEqualTo(formatted("let a:number=1+2;println(a);\n\n\nlet b : string = \"ñandú 🐧\";println(b);"))
        assertThat(format("let   a  :  number   =   1;", """{"mandatory-single-space-separation":true}"""))
            .isEqualTo(formatted("let a : number = 1;"))
    }

    @Test
    fun `version 1_1 indents the body of an if`() {
        assertThat(format("if (true) {\nprintln(1);\n}", """{"indent-inside-if":4}""", LanguageVersion.V1_1))
            .isEqualTo(formatted("if (true) {\n    println(1);\n}"))
    }

    @Test
    fun `version 1_1 can open lines inside a compact if before indenting them`() {
        assertThat(
            format(
                "if(true){println(1);}",
                """{"mandatory-line-breaks-inside-if":true,"indent-inside-if":2}""",
                LanguageVersion.V1_1,
            ),
        ).isEqualTo(formatted("if(true){\n  println(1);\n}"))
    }

    @Test
    fun `a lexical error is unformattable code with its position, not a failure`() {
        val outcome = format("println(\"abc);", "{}") as FormattingOutcome.Unformattable

        assertThat(outcome.diagnostic.rule).isEqualTo(DiagnosticRule.UNTERMINATED_STRING)
        assertThat(outcome.diagnostic.line).isEqualTo(1)
        assertThat(outcome.diagnostic.column).isEqualTo(9)
    }

    @Test
    fun `a semantic error does not stop formatting because only whitespace changes`() {
        assertThat(format("let a:number=1;let a:number=2;", """{"enforce-spacing-around-equals":true}"""))
            .isEqualTo(formatted("let a:number = 1;let a:number = 2;"))
    }

    @Test
    fun `rules are checked against the catalog of the requested version`() {
        val violations =
            check(
                """
                {"unknown":true,"if-brace-same-line":true,"enforce-spacing-around-equals":"true",
                 "line-breaks-after-println":1.5,"mandatory-line-break-after-statement":1}
                """,
            )

        assertThat(violations).containsExactlyInAnyOrder(
            RuleViolation("unknown", "unknown-rule"),
            RuleViolation("if-brace-same-line", "unavailable-in-version"),
            RuleViolation("enforce-spacing-around-equals", "wrong-type"),
            RuleViolation("line-breaks-after-println", "wrong-type"),
            RuleViolation("mandatory-line-break-after-statement", "wrong-type"),
        )
    }

    @Test
    fun `numeric rules stay between zero and the configured maximum, and null turns them off`() {
        assertThat(check("""{"line-breaks-after-println":-1}"""))
            .containsExactly(RuleViolation("line-breaks-after-println", "out-of-range"))
        assertThat(check("""{"line-breaks-after-println":11}"""))
            .containsExactly(RuleViolation("line-breaks-after-println", "out-of-range"))
        assertThat(check("""{"line-breaks-after-println":100000000000000000000}"""))
            .containsExactly(RuleViolation("line-breaks-after-println", "out-of-range"))
        assertThat(check("""{"line-breaks-after-println":10}""")).isEmpty()
        assertThat(check("""{"line-breaks-after-println":null}""")).isEmpty()
    }

    @Test
    fun `conflicting rules are reported by the formatter of the library`() {
        assertThat(check("""{"enforce-spacing-around-equals":true,"enforce-no-spacing-around-equals":true}"""))
            .containsExactlyInAnyOrder(
                RuleViolation("enforce-spacing-around-equals", "conflicting-rules"),
                RuleViolation("enforce-no-spacing-around-equals", "conflicting-rules"),
            )
        assertThat(check("""{"if-brace-same-line":true,"if-brace-below-line":true}""", LanguageVersion.V1_1))
            .containsExactlyInAnyOrder(
                RuleViolation("if-brace-same-line", "conflicting-rules"),
                RuleViolation("if-brace-below-line", "conflicting-rules"),
            )
    }

    @Test
    fun `invalid rules are reported before formatting any code`() {
        assertThat(format("println(\"abc);", """{"unknown":true}"""))
            .isEqualTo(FormattingOutcome.InvalidRules(listOf(RuleViolation("unknown", "unknown-rule"))))
    }

    @Test
    fun `the code limit counts UTF-8 bytes`() {
        val small = FormattingService(FormattingLimits(codeBytes = 4))

        assertThat(small.format(LanguageVersion.V1_0, "ñññ", emptyMap())).isEqualTo(FormattingOutcome.CodeTooLarge)
        assertThat(small.format(LanguageVersion.V1_0, "1;", emptyMap())).isEqualTo(formatted("1;"))
    }

    @Test
    fun `a result above the output limit is rejected instead of cut`() {
        val code = "println(1);println(2);"
        val rules = rules("""{"line-breaks-after-println":10}""")

        assertThat(FormattingService(FormattingLimits(outputBytes = 30)).format(LanguageVersion.V1_0, code, rules))
            .isEqualTo(FormattingOutcome.OutputTooLarge)
        assertThat(FormattingService(FormattingLimits(outputBytes = 33)).format(LanguageVersion.V1_0, code, rules))
            .isEqualTo(formatted("println(1);" + "\n".repeat(11) + "println(2);"))
    }

    private fun formatted(code: String) = FormattingOutcome.Formatted(code)

    private fun format(
        code: String,
        rules: String,
        version: LanguageVersion = LanguageVersion.V1_0,
    ): FormattingOutcome = service.format(version, code, rules(rules))

    private fun check(
        rules: String,
        version: LanguageVersion = LanguageVersion.V1_0,
    ): List<RuleViolation> = service.check(version, rules(rules))

    private fun rules(json: String): Map<String, JsonNode> =
        mapper.readTree(json).properties().associate { it.key to it.value }
}
