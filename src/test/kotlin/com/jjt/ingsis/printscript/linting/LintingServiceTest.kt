package com.jjt.ingsis.printscript.linting

import com.jjt.ingsis.printscript.language.LanguageVersion
import com.jjt.ingsis.printscript.language.PrintScriptToolchains
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import printscript.linter.DiagnosticReadResult
import printscript.v1.linter.PrintScriptV1LinterFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class LintingServiceTest {
    private val service = LintingService(LintingLimits())
    private val json = JsonMapper.builder().build()

    private fun rules(text: String): Map<String, JsonNode> =
        json.readTree(text).properties().associate {
            it.key to
                it.value
        }

    @Test
    fun `explicit empty selection is not the library default`() {
        val code = "println(1 + 2);"
        val defaults =
            PrintScriptV1LinterFactory.create().lint(
                PrintScriptToolchains.forVersion(LanguageVersion.V1_0).statements(code),
            )
        assertThat(defaults.nextDiagnostic()).isInstanceOf(DiagnosticReadResult.Success::class.java)
        LanguageVersion.entries.forEach {
            val result = service.lint(it, code, emptyMap()) as LintingOutcome.Analyzed
            assertThat(result.diagnostics).isEmpty()
        }
    }

    @Test
    fun `enabled and explicitly disabled rules produce different results`() {
        val selected = rules("""{"mandatory-variable-or-literal-in-println":true}""")
        val result = service.lint(LanguageVersion.V1_0, "println(1+2);", selected) as LintingOutcome.Analyzed
        assertThat(result.diagnostics.single().rule).isEqualTo("mandatory-variable-or-literal-in-println")
        assertThat(result.diagnostics.single().column).isPositive()
        assertThat(
            service.lint(
                LanguageVersion.V1_0,
                "println(1+2);",
                rules("""{"mandatory-variable-or-literal-in-println":false}"""),
            ),
        ).isEqualTo(LintingOutcome.Analyzed(emptyList()))
    }

    @Test
    fun `lexical and syntactic errors never become lint findings even with no rules`() {
        for (code in listOf("println(\"unfinished);", "let = ;", "println(1); let = ;")) {
            assertThat(service.lint(LanguageVersion.V1_0, code, emptyMap()))
                .isInstanceOf(LintingOutcome.InvalidCode::class.java)
        }
    }

    @Test
    fun `catalog and choice validation come from the published library`() {
        val catalog = service.catalog(LanguageVersion.V1_0)
        assertThat(catalog).isEqualTo(PrintScriptV1LinterFactory.configurationRules())
        val choice = catalog.single { it.id == "identifier_format" }
        assertThat(choice.choices).isNotEmpty()
        for (option in choice.choices) {
            assertThat(service.check(LanguageVersion.V1_0, rules(json.writeValueAsString(mapOf(choice.id to option)))))
                .isEmpty()
        }
        assertThat(service.check(LanguageVersion.V1_0, rules("""{"identifier_format":"made-up"}""")).single().reason)
            .isEqualTo("unknown-choice")
        assertThat(service.check(LanguageVersion.V1_0, rules("""{"identifier_format":true}""")).single().reason)
            .isEqualTo("wrong-type")
        assertThat(service.check(LanguageVersion.V1_0, rules("""{"identifier_format":null}"""))).isEmpty()
    }

    @Test
    fun `unknown version-specific and wrong type options are invalid configuration`() {
        val checks =
            service.check(
                LanguageVersion.V1_0,
                rules("""{"unknown":true,"mandatory-variable-or-literal-in-readInput":true,"identifier_format":2}"""),
            )
        assertThat(checks).hasSize(3)
        assertThat(service.lint(LanguageVersion.V1_0, "println(1);", rules("""{"unknown":true}""")))
            .isInstanceOf(LintingOutcome.InvalidRules::class.java)
    }

    @Test
    fun `Unicode preserves source positions and code size uses UTF8 bytes`() {
        val code = "println(\"ñ 🐧\");\nprintln(1+2);"
        val result =
            service.lint(
                LanguageVersion.V1_1,
                code,
                rules("""{"mandatory-variable-or-literal-in-println":true}"""),
            ) as LintingOutcome.Analyzed
        assertThat(result.diagnostics.single().line).isEqualTo(2)
        assertThat(LintingService(LintingLimits(codeBytes = 3)).lint(LanguageVersion.V1_0, "🐧", emptyMap()))
            .isEqualTo(LintingOutcome.CodeTooLarge)
    }

    @Test
    fun `diagnostic limit rejects incomplete analysis instead of declaring compliance`() {
        val result =
            LintingService(LintingLimits(diagnosticLimit = 1)).lint(
                LanguageVersion.V1_0,
                "println(1+2);println(2+3);",
                rules("""{"mandatory-variable-or-literal-in-println":true}"""),
            )
        assertThat(result).isEqualTo(LintingOutcome.DiagnosticsTooLarge)
    }
}
