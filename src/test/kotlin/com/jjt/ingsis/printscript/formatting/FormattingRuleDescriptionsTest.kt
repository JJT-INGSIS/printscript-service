package com.jjt.ingsis.printscript.formatting

import com.jjt.ingsis.printscript.language.LanguageVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FormattingRuleDescriptionsTest {
    @Test
    fun `every rule of the library has exactly one description`() {
        val libraryRules =
            LanguageVersion.entries
                .flatMap { PrintScriptFormatters.rules(it) }
                .map { it.id }
                .toSet()

        assertThat(FormattingRuleDescriptions.describedRules).isEqualTo(libraryRules)
    }
}
