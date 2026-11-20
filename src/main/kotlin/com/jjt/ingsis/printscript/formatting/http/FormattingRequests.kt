package com.jjt.ingsis.printscript.formatting.http

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode

data class FormattingRulesCheckRequest(
    val version: String,
    val rules: Map<String, JsonNode?>,
)

data class FormattingRequest(
    val code: String,
    val version: String,
    val rules: Map<String, JsonNode?>,
)

internal fun Map<String, JsonNode?>.withExplicitNulls(): Map<String, JsonNode> =
    mapValues { (_, value) -> value ?: NullNode.instance }
