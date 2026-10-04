package com.jjt.ingsis.printscript.validation

import com.jjt.ingsis.printscript.diagnostic.DiagnosticRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class ValidationContractTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @TestFactory
    fun `every documented example describes the real behaviour`(): List<DynamicTest> =
        ContractDocument.examples
            .filter { example -> example.status < SERVER_ERROR }
            .map { example -> dynamicTest(example.title) { verify(example) } }

    @Test
    fun `the document shows valid code, invalid code, a rejected request and a technical failure`() {
        assertThat(ContractDocument.examples.map { example -> example.status })
            .contains(OK, BAD_REQUEST, UNPROCESSABLE_CONTENT, SERVER_ERROR)
    }

    @Test
    fun `the document lists every rule that a diagnostic can name`() {
        assertThat(ContractDocument.text).contains(DiagnosticRule.entries.map { rule -> "`${rule.name}`" })
    }

    @Test
    fun `rejects a body that is not JSON`() {
        mockMvc
            .post("/validate") {
                contentType = MediaType.APPLICATION_JSON
                content = "println(1);"
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }
    }

    @Test
    fun `rejects a null code instead of treating it as empty`() {
        mockMvc
            .post("/validate") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"code": null, "version": "1.0"}"""
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            }
    }

    private fun verify(example: ContractExample) {
        mockMvc
            .post("/validate") {
                contentType = MediaType.APPLICATION_JSON
                content = example.request
            }.andExpect {
                status { isEqualTo(example.status) }
                content { json(example.response, JsonCompareMode.STRICT) }
            }
    }

    private companion object {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val UNPROCESSABLE_CONTENT = 422
        const val SERVER_ERROR = 500
    }
}
