package com.jjt.ingsis.printscript.language

class UnsupportedVersionException(
    val requested: String,
    val supported: List<String>,
) : RuntimeException("PrintScript version '$requested' is not supported")
