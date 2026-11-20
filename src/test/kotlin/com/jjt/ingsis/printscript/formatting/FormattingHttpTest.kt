package com.jjt.ingsis.printscript.formatting

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockMvcResultMatchersDsl
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest(properties = ["formatting.code-bytes=64", "formatting.output-bytes=64"])
@AutoConfigureMockMvc
class FormattingHttpTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `the catalog describes each rule of the version with its type and limits`() {
        mockMvc.get("/formatting/rules?version=1.0").andExpect {
            status { isOk() }
            jsonPath("$.version") { value("1.0") }
            jsonPath("$.rules.length()") { value(8) }
            jsonPath("$.rules[?(@.id == 'if-brace-same-line')]") { isEmpty() }
        }
        mockMvc.get("/formatting/rules?version=1.1").andExpect {
            status { isOk() }
            jsonPath("$.rules.length()") { value(12) }
            jsonPath("$.rules[0].id") { value("enforce-no-spacing-around-equals") }
            jsonPath("$.rules[0].type") { value("boolean") }
            jsonPath("$.rules[0].default") { value(false) }
            jsonPath("$.rules[0].minimum") { value(null) }
            jsonPath("$.rules[0].conflictsWith[0]") { value("enforce-spacing-around-equals") }
            jsonPath("$.rules[0].description") { value("Ningún espacio alrededor de cada =.") }
            jsonPath("$.rules[?(@.id == 'indent-inside-if')].type") { value("integer") }
            jsonPath("$.rules[?(@.id == 'indent-inside-if')].default") { value(null) }
            jsonPath("$.rules[?(@.id == 'indent-inside-if')].minimum") { value(0) }
            jsonPath("$.rules[?(@.id == 'indent-inside-if')].maximum") { value(10) }
        }
    }

    @Test
    fun `an unsupported or missing version is a rejected request in every operation`() {
        mockMvc.get("/formatting/rules?version=9.9").andExpect { unsupportedVersion() }
        post("/formatting/rules/check", """{"version":"9.9","rules":{}}""").andExpect { unsupportedVersion() }
        post("/formatting", """{"code":"1;","version":"9.9","rules":{}}""").andExpect { unsupportedVersion() }
        mockMvc.get("/formatting/rules").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `checking rules answers whether the configuration is valid`() {
        post(
            "/formatting/rules/check",
            """{"version":"1.1","rules":{"indent-inside-if":4,"line-breaks-after-println":null}}""",
        ).andExpect {
            status { isOk() }
            content { json("""{"valid":true,"violations":[]}""", JsonCompareMode.STRICT) }
        }
        post("/formatting/rules/check", """{"version":"1.0","rules":{"indent-inside-if":4}}""").andExpect {
            status { isOk() }
            content {
                json(
                    """{"valid":false,"violations":[{"rule":"indent-inside-if","reason":"unavailable-in-version"}]}""",
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `formatting answers the formatted code or the diagnostic that prevents it`() {
        post(
            "/formatting",
            """{"code":"let a:number=1;","version":"1.0","rules":{"enforce-spacing-around-equals":true}}""",
        ).andExpect {
            status { isOk() }
            content {
                json("""{"formatted":true,"code":"let a:number = 1;","diagnostics":[]}""", JsonCompareMode.STRICT)
            }
        }
        post("/formatting", """{"code":"println(\"abc);","version":"1.0","rules":{}}""").andExpect {
            status { isOk() }
            content {
                json(
                    """
                    {"formatted":false,"code":null,"diagnostics":[{"rule":"UNTERMINATED_STRING",
                     "message":"falta cerrar el texto abierto con \"","line":1,"column":9}]}
                    """,
                    JsonCompareMode.STRICT,
                )
            }
        }
    }

    @Test
    fun `invalid rules and limits are typed problems`() {
        post("/formatting", """{"code":"1;","version":"1.0","rules":{"unknown":true}}""").andExpect {
            problem(422, "invalid-formatting-rules")
            jsonPath("$.violations[0].rule") { value("unknown") }
            jsonPath("$.violations[0].reason") { value("unknown-rule") }
        }
        post("/formatting", """{"code":"${"1;".repeat(33)}","version":"1.0","rules":{}}""").andExpect {
            problem(400, "request-too-large")
        }
        post(
            "/formatting",
            """{"code":"${"println(1);".repeat(4)}","version":"1.0","rules":{"line-breaks-after-println":10}}""",
        ).andExpect { problem(422, "formatted-output-too-large") }
    }

    @Test
    fun `a malformed body is a bad request`() {
        post("/formatting", "println(1);").andExpect { status { isBadRequest() } }
        post("/formatting", """{"code":null,"version":"1.0","rules":{}}""").andExpect { status { isBadRequest() } }
        post("/formatting", """{"code":"1;","version":"1.0"}""").andExpect { status { isBadRequest() } }
        post("/formatting", """{"code":"1;","version":"1.0","rules":[]}""").andExpect { status { isBadRequest() } }
    }

    private fun post(
        path: String,
        body: String,
    ): ResultActionsDsl =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun MockMvcResultMatchersDsl.unsupportedVersion() {
        status { isEqualTo(422) }
        content { json("""{"status":422,"supportedVersions":["1.0","1.1"]}""", JsonCompareMode.LENIENT) }
    }

    private fun MockMvcResultMatchersDsl.problem(
        code: Int,
        reason: String,
    ) {
        status { isEqualTo(code) }
        content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
        jsonPath("$.type") { value("urn:printscript:problem:$reason") }
    }
}
