package com.jjt.ingsis.printscript.formatting

object FormattingRuleDescriptions {
    private val descriptions =
        mapOf(
            "enforce-spacing-around-equals" to "Un espacio antes y después de cada =.",
            "enforce-no-spacing-around-equals" to "Ningún espacio alrededor de cada =.",
            "enforce-spacing-before-colon-in-declaration" to "Un espacio antes de los dos puntos de una declaración.",
            "enforce-spacing-after-colon-in-declaration" to "Un espacio después de los dos puntos de una declaración.",
            "mandatory-single-space-separation" to "Un solo espacio entre tokens de una misma línea.",
            "mandatory-space-surrounding-operations" to "Un espacio a cada lado de los operadores +, -, * y /.",
            "mandatory-line-break-after-statement" to "Un salto de línea después de cada sentencia.",
            "line-breaks-after-println" to "Cantidad de líneas en blanco después de cada println.",
            "if-brace-same-line" to "La llave que abre un if va en la misma línea.",
            "if-brace-below-line" to "La llave que abre un if va en la línea siguiente.",
            "indent-inside-if" to "Cantidad de espacios de sangría dentro de un if.",
            "mandatory-line-breaks-inside-if" to "El contenido de cada if va en líneas separadas.",
        )

    val describedRules: Set<String> = descriptions.keys

    fun of(rule: String): String = descriptions[rule] ?: error("The formatting rule $rule has no description")
}
