package com.jjt.ingsis.printscript.linting

import com.jjt.ingsis.printscript.language.LanguageVersion
import printscript.linter.Linter
import printscript.v1.configuration.ConfigurationCheck
import printscript.v1.configuration.ConfigurationRule
import printscript.v1.linter.PrintScriptV11LinterConfigurationResult
import printscript.v1.linter.PrintScriptV11LinterFactory
import printscript.v1.linter.PrintScriptV1LinterConfigurationResult
import printscript.v1.linter.PrintScriptV1LinterFactory

internal object PrintScriptLinters {
    fun rules(version: LanguageVersion): List<ConfigurationRule> =
        when (version) {
            LanguageVersion.V1_0 -> PrintScriptV1LinterFactory.configurationRules()
            LanguageVersion.V1_1 -> PrintScriptV11LinterFactory.configurationRules()
        }

    fun check(
        version: LanguageVersion,
        json: String,
    ): ConfigurationCheck =
        when (version) {
            LanguageVersion.V1_0 -> PrintScriptV1LinterFactory.checkConfiguration(json)
            LanguageVersion.V1_1 -> PrintScriptV11LinterFactory.checkConfiguration(json)
        }

    // Explicit configuration, including {}, never falls back to factory defaults.
    fun create(
        version: LanguageVersion,
        json: String,
    ): Linter =
        when (version) {
            LanguageVersion.V1_0 -> {
                when (val config = PrintScriptV1LinterFactory.configurationFrom(json)) {
                    is PrintScriptV1LinterConfigurationResult.Success -> {
                        PrintScriptV1LinterFactory.create(
                            config.configuration,
                        )
                    }

                    is PrintScriptV1LinterConfigurationResult.Failure -> {
                        error(
                            "Configuration rejected after successful check",
                        )
                    }
                }
            }

            LanguageVersion.V1_1 -> {
                when (val config = PrintScriptV11LinterFactory.configurationFrom(json)) {
                    is PrintScriptV11LinterConfigurationResult.Success -> {
                        PrintScriptV11LinterFactory.create(
                            config.configuration,
                        )
                    }

                    is PrintScriptV11LinterConfigurationResult.Failure -> {
                        error("Configuration rejected after successful check")
                    }
                }
            }
        }
}
