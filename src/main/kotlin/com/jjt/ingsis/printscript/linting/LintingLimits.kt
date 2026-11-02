package com.jjt.ingsis.printscript.linting

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("linting")
data class LintingLimits(
    val codeBytes: Int = 1_048_576,
    val diagnosticLimit: Int = 1000,
) {
    init {
        require(codeBytes > 0 && diagnosticLimit > 0)
    }

    fun canonical(): String = "codeBytes=$codeBytes;diagnosticLimit=$diagnosticLimit"
}

@Configuration
@EnableConfigurationProperties(LintingLimits::class)
class LintingConfiguration
