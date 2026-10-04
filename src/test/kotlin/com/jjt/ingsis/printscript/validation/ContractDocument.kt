package com.jjt.ingsis.printscript.validation

import java.io.File

data class ContractExample(
    val title: String,
    val status: Int,
    val request: String,
    val response: String,
)

object ContractDocument {
    private const val EXAMPLE_HEADING_PREFIX = "#### "

    private val sectionStart = Regex("^(?=#{1,6} )", RegexOption.MULTILINE)
    private val exampleHeading = Regex("^#### `(\\d{3})` (.+)")
    private val jsonBlock = Regex("```json\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)

    val text: String = File("docs/validation.md").readText().replace("\r\n", "\n")

    val examples: List<ContractExample> =
        text
            .split(sectionStart)
            .filter { section -> section.startsWith(EXAMPLE_HEADING_PREFIX) }
            .map(::exampleIn)

    private fun exampleIn(section: String): ContractExample {
        val heading =
            checkNotNull(exampleHeading.find(section)) {
                "An example heading must look like: #### `200` Title. Found: ${section.lineSequence().first()}"
            }
        val blocks = jsonBlock.findAll(section).map { block -> block.groupValues[1] }.toList()

        check(blocks.size == 2) {
            "The example '${heading.groupValues[2]}' must have a request block and a response block"
        }

        return ContractExample(
            title = heading.groupValues[2],
            status = heading.groupValues[1].toInt(),
            request = blocks[0],
            response = blocks[1],
        )
    }
}
