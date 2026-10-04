package com.jjt.ingsis.printscript.language

enum class LanguageVersion(
    val label: String,
) {
    V1_0("1.0"),
    V1_1("1.1"),
    ;

    companion object {
        val supportedLabels: List<String> = entries.map { it.label }

        fun fromLabel(label: String): LanguageVersion =
            entries.firstOrNull { it.label == label }
                ?: throw UnsupportedVersionException(requested = label, supported = supportedLabels)
    }
}
