package com.jjt.ingsis.printscript.linting

import com.jjt.ingsis.printscript.language.LanguageVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import printscript.v1.linter.PrintScriptV11LinterConfigurationResult
import printscript.v1.linter.PrintScriptV11LinterFactory
import printscript.v1.linter.PrintScriptV1LinterConfigurationResult
import printscript.v1.linter.PrintScriptV1LinterFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class LintDefaultsTest {
    private val json = JsonMapper.builder().build()
    private val service = LintingService(LintingLimits())

    @Test
    fun `exported selections round trip to the actual published factory defaults for each version`() {
        val v1 = json.writeValueAsString(service.defaults(LanguageVersion.V1_0))
        val v11 = json.writeValueAsString(service.defaults(LanguageVersion.V1_1))
        val parsed1 = PrintScriptV1LinterFactory.configurationFrom(v1) as PrintScriptV1LinterConfigurationResult.Success
        val parsed11 =
            PrintScriptV11LinterFactory.configurationFrom(
                v11,
            ) as PrintScriptV11LinterConfigurationResult.Success
        assertThat(parsed1.configuration.rules).isEqualTo(PrintScriptV1LinterFactory.defaultConfiguration().rules)
        assertThat(parsed11.configuration.rules).isEqualTo(PrintScriptV11LinterFactory.defaultConfiguration().rules)
    }

    @Test
    fun `exported defaults are valid catalog selections and differ from explicit empty rules`() {
        LanguageVersion.entries.forEach { version ->
            val tree = json.valueToTree<JsonNode>(service.defaults(version))
            val selection = tree.properties().associate { it.key to it.value }
            assertThat(service.check(version, selection)).isEmpty()
            val result = service.lint(version, "println(1+2);", selection) as LintingOutcome.Analyzed
            assertThat(result.diagnostics).isNotEmpty()
            assertThat(service.lint(version, "println(1+2);", emptyMap()))
                .isEqualTo(LintingOutcome.Analyzed(emptyList()))
        }
    }
}
