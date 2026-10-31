package com.jjt.ingsis.printscript.processor

import java.io.File
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat

fun interface ProcessorArtifact {
    fun digest(): String?
}

class RunningJar(
    private val classPath: String,
) : ProcessorArtifact {
    override fun digest(): String? {
        val entries = classPath.split(File.pathSeparator).filter(String::isNotBlank)
        val jar = if (entries.size == 1) File(entries.single()) else null
        return if (jar != null && jar.isFile && jar.name.endsWith(".jar")) sha256(jar) else null
    }

    private fun sha256(jar: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(jar.inputStream(), digest).use { it.transferTo(OutputStream.nullOutputStream()) }
        return HexFormat.of().formatHex(digest.digest())
    }
}
