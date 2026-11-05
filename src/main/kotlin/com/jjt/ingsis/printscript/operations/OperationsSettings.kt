package com.jjt.ingsis.printscript.operations

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

private const val RETENTION_DAYS = 7L
private const val ADMISSION_MINUTES = 2L
private const val RECORD_BYTES = 16_777_216
private const val RECORD_LIMIT = 256

@ConfigurationProperties("operations")
data class OperationsSettings(
    val enabled: Boolean = false,
    val deploymentMode: String = "",
    val namespace: String = "printscript:local:operations",
    val retention: Duration = Duration.ofDays(RETENTION_DAYS),
    val admissionWindow: Duration = Duration.ofMinutes(ADMISSION_MINUTES),
    val maximumBytes: Int = RECORD_BYTES,
    val maximumRecords: Int = RECORD_LIMIT,
) {
    init {
        require(!enabled || deploymentMode == "single-stop-first")
        require(namespace.isNotBlank() && retention > admissionWindow && !admissionWindow.isNegative)
        require(maximumBytes > 0 && maximumRecords > 0)
    }
}
