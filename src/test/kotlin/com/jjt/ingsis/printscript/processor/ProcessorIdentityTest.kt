package com.jjt.ingsis.printscript.processor

import com.jjt.ingsis.printscript.execution.ExecutionLimits
import com.jjt.ingsis.printscript.formatting.FormattingLimits
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat

class ProcessorIdentityTest {
    private val revision = Regex("^sha256-v1:[0-9a-f]{64}$").toPattern()

    @Test
    fun `the same artifact and limits always give the same capability revisions`() {
        val first = ProcessorIdentity({ "a".repeat(64) }, ExecutionLimits(), FormattingLimits())
        val second = ProcessorIdentity({ "a".repeat(64) }, ExecutionLimits(), FormattingLimits())

        assertThat(first.validation).matches(revision).isEqualTo(second.validation)
        assertThat(first.execution).matches(revision).isEqualTo(second.execution)
        assertThat(first.formatting).matches(revision).isEqualTo(second.formatting)
        assertThat(first.validation).isNotEqualTo(first.execution)
        assertThat(first.formatting).isNotEqualTo(first.validation).isNotEqualTo(first.execution)
    }

    @Test
    fun `execution limits change only the execution revision`() {
        val usual = ProcessorIdentity({ "a".repeat(64) }, ExecutionLimits(), FormattingLimits())
        val shorter = ProcessorIdentity({ "a".repeat(64) }, ExecutionLimits(totalSeconds = 60), FormattingLimits())

        assertThat(shorter.validation).isEqualTo(usual.validation)
        assertThat(shorter.execution).isNotEqualTo(usual.execution)
        assertThat(shorter.formatting).isEqualTo(usual.formatting)
    }

    @Test
    fun `formatting limits change only the formatting revision`() {
        val usual = ProcessorIdentity({ "a".repeat(64) }, ExecutionLimits(), FormattingLimits())
        val stricter = ProcessorIdentity({ "a".repeat(64) }, ExecutionLimits(), FormattingLimits(maximumCount = 4))

        assertThat(stricter.formatting).isNotEqualTo(usual.formatting)
        assertThat(stricter.validation).isEqualTo(usual.validation)
        assertThat(stricter.execution).isEqualTo(usual.execution)
    }

    @Test
    fun `another artifact changes every revision`() {
        val first = ProcessorIdentity({ "a".repeat(64) }, ExecutionLimits(), FormattingLimits())
        val other = ProcessorIdentity({ "b".repeat(64) }, ExecutionLimits(), FormattingLimits())

        assertThat(other.validation).isNotEqualTo(first.validation)
        assertThat(other.execution).isNotEqualTo(first.execution)
        assertThat(other.formatting).isNotEqualTo(first.formatting)
    }

    @Test
    fun `without an artifact there is no revision to claim`() {
        val identity = ProcessorIdentity({ null }, ExecutionLimits(), FormattingLimits())

        assertThat(identity.validation).isNull()
        assertThat(identity.execution).isNull()
        assertThat(identity.formatting).isNull()
        assertThat(identity.revisionFor("/validate")).isNull()
        assertThat(identity.manifest()["artifact"]).isNull()
    }

    @Test
    fun `each route answers with the revision of the capability it uses`() {
        val identity = ProcessorIdentity({ "a".repeat(64) }, ExecutionLimits(), FormattingLimits())

        assertThat(identity.revisionFor("/validate")).isEqualTo(identity.validation)
        assertThat(identity.revisionFor("/executions")).isEqualTo(identity.execution)
        assertThat(identity.revisionFor("/executions/x/events")).isEqualTo(identity.execution)
        assertThat(identity.revisionFor("/formatting")).isEqualTo(identity.formatting)
        assertThat(identity.revisionFor("/formatting/rules")).isEqualTo(identity.formatting)
        assertThat(identity.revisionFor("/formattingx")).isNull()
        assertThat(identity.revisionFor("/processor")).isNull()
    }

    @Test
    fun `the running jar digest is the sha256 of its bytes and only a single jar counts`() {
        val jar = Files.createTempFile("app", ".jar").toFile().apply { writeText("contenido del jar") }
        val expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar.readBytes()))

        assertThat(RunningJar(jar.path).digest()).isEqualTo(expected)
        assertThat(RunningJar(jar.path + File.pathSeparator + jar.parent).digest()).isNull()
        assertThat(RunningJar(jar.parent).digest()).isNull()
        assertThat(RunningJar("").digest()).isNull()
    }
}
