package com.jjt.ingsis.printscript.diagnostic

data class Diagnostic(
    val rule: DiagnosticRule,
    val message: String,
    val line: Int,
    val column: Int,
)
