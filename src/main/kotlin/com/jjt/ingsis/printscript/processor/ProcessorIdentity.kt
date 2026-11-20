package com.jjt.ingsis.printscript.processor

import com.jjt.ingsis.printscript.execution.ExecutionLimits
import com.jjt.ingsis.printscript.formatting.FormattingLimits
import com.jjt.ingsis.printscript.linting.LintingLimits
import java.security.MessageDigest
import java.util.HexFormat

const val PROCESSOR_REVISION_HEADER = "Processor-Revision"

class ProcessorIdentity(
    artifact: ProcessorArtifact,
    limits: ExecutionLimits,
    formattingLimits: FormattingLimits,
    lintingLimits: LintingLimits = LintingLimits(),
) {
    val artifactDigest: String? = artifact.digest()
    val validation: String? = revision("validate", "")
    val execution: String? = revision("execute", limits.canonical())
    val formatting: String? = revision("format", formattingLimits.canonical())
    val linting: String? = revision("lint", lintingLimits.canonical())

    fun revisionFor(path: String): String? =
        when {
            path == "/validate" -> validation
            path == "/linting" || path.startsWith("/linting/") -> linting
            path == "/executions" || path.startsWith("/executions/") -> execution
            path == "/formatting" || path.startsWith("/formatting/") -> formatting
            else -> null
        }

    fun manifest(): Map<String, Any?> =
        mapOf(
            "artifact" to artifactDigest?.let { "sha256:$it" },
            "capabilities" to
                mapOf("validate" to validation, "execute" to execution, "format" to formatting, "lint" to linting),
        )

    private fun revision(
        capability: String,
        configuration: String,
    ): String? {
        val digest = artifactDigest ?: return null
        val canonical = "$capability|$digest|$configuration".toByteArray(Charsets.UTF_8)
        return "sha256-v1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical))
    }
}

private fun ExecutionLimits.canonical(): String =
    listOf(
        "activeGlobal=$activeGlobal",
        "totalSeconds=$totalSeconds",
        "inputWaitSeconds=$inputWaitSeconds",
        "reconnectSeconds=$reconnectSeconds",
        "codeBytes=$codeBytes",
        "inputBytes=$inputBytes",
        "outputBytes=$outputBytes",
        "bufferEvents=$bufferEvents",
        "bufferBytes=$bufferBytes",
        "terminalRetentionSeconds=$terminalRetentionSeconds",
    ).joinToString(";")
