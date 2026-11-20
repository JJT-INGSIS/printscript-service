package com.jjt.ingsis.printscript.formatting

import com.jjt.ingsis.printscript.language.LanguageVersion
import printscript.formatter.Formatter
import printscript.v1.configuration.ConfigurationCheck
import printscript.v1.configuration.ConfigurationRule
import printscript.v1.formatter.PrintScriptV11FormatterFactory
import printscript.v1.formatter.PrintScriptV1FormatterFactory
import printscript.v1.formatter.configuration.PrintScriptV11FormatterConfiguration
import printscript.v1.formatter.configuration.PrintScriptV11FormatterConfigurationResult
import printscript.v1.formatter.configuration.PrintScriptV1FormatterConfiguration
import printscript.v1.formatter.configuration.PrintScriptV1FormatterConfigurationResult

internal object PrintScriptFormatters {
    fun rules(version: LanguageVersion): List<ConfigurationRule> =
        when (version) {
            LanguageVersion.V1_0 -> PrintScriptV1FormatterConfiguration.rules()
            LanguageVersion.V1_1 -> PrintScriptV11FormatterConfiguration.rules()
        }

    fun check(
        version: LanguageVersion,
        configuration: String,
    ): ConfigurationCheck =
        when (version) {
            LanguageVersion.V1_0 -> PrintScriptV1FormatterConfiguration.check(configuration)
            LanguageVersion.V1_1 -> PrintScriptV11FormatterConfiguration.check(configuration)
        }

    fun create(
        version: LanguageVersion,
        configuration: String,
    ): Formatter =
        when (version) {
            LanguageVersion.V1_0 -> createV10(configuration)
            LanguageVersion.V1_1 -> createV11(configuration)
        }

    private fun createV10(configuration: String): Formatter =
        when (val result = PrintScriptV1FormatterConfiguration.fromJson(configuration)) {
            is PrintScriptV1FormatterConfigurationResult.Success -> {
                PrintScriptV1FormatterFactory.create(
                    result.configuration,
                )
            }

            is PrintScriptV1FormatterConfigurationResult.Failure -> {
                rejectedAfterCheck(result.error)
            }
        }

    private fun createV11(configuration: String): Formatter =
        when (val result = PrintScriptV11FormatterConfiguration.fromJson(configuration)) {
            is PrintScriptV11FormatterConfigurationResult.Success -> {
                PrintScriptV11FormatterFactory.create(
                    result.configuration,
                )
            }

            is PrintScriptV11FormatterConfigurationResult.Failure -> {
                rejectedAfterCheck(result.error)
            }
        }

    private fun rejectedAfterCheck(error: Any): Nothing =
        error("The formatter rejected a configuration that its check accepted: $error")
}
