package com.jjt.ingsis.printscript.formatting

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("formatting")
data class FormattingLimits(
    val codeBytes: Int = 1_048_576,
    val outputBytes: Int = 2_097_152,
    val maximumCount: Int = 10,
) {
    init {
        require(codeBytes > 0 && outputBytes > 0) { "Formatting sizes must be positive" }
        require(maximumCount >= 0) { "The largest rule value cannot be negative" }
    }

    fun canonical(): String = "codeBytes=$codeBytes;outputBytes=$outputBytes;maximumCount=$maximumCount"
}
