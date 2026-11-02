package com.jjt.ingsis.printscript.linting

import com.jjt.ingsis.printscript.processor.PROCESSOR_REVISION_HEADER
import com.jjt.ingsis.printscript.processor.ProcessorIdentity
import com.jjt.ingsis.printscript.processor.ProcessorRevisionHttpTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@Import(ProcessorRevisionHttpTest.KnownArtifact::class)
class LintingHttpTest(
    @Autowired private val http: MockMvc,
    @Autowired private val identity: ProcessorIdentity,
) {
    @Test
    fun `catalog and processor manifest expose the actual lint capability`() {
        http.get("/processor").andExpect {
            status { isOk() }
            jsonPath("$.capabilities.lint") { value(identity.linting) }
        }
        http.get("/linting/rules?version=1.1").andExpect {
            status { isOk() }
            header { string(PROCESSOR_REVISION_HEADER, identity.linting!!) }
            jsonPath("$.rules[?(@.id == 'identifier_format')].type") { value("choice") }
        }
        assertThat(identity.linting).isNotNull().isNotEqualTo(identity.formatting)
    }

    @Test
    fun `missing rules is rejected while empty selection complies`() {
        http
            .post("/linting") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"version":"1.0","code":"println(1+2);"}"""
            }.andExpect {
                status { isBadRequest() }
                header { string(PROCESSOR_REVISION_HEADER, identity.linting!!) }
            }
        http
            .post("/linting") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"version":"1.0","code":"println(1+2);","rules":{}}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.compliant") { value(true) }
            }
    }

    @Test
    fun `syntax errors have no compliance value and revisions survive rejection`() {
        http
            .post("/linting") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"version":"1.0","code":"let = ;","rules":{}}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.analyzed") { value(false) }
                jsonPath("$.compliant") { value(null) }
            }
        http
            .post("/linting/rules/check") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"version":"1.0","rules":{"identifier_format":"fake"}}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.valid") { value(false) }
                header { string(PROCESSOR_REVISION_HEADER, identity.linting!!) }
            }
        http.get("/linting/rules?version=9.9").andExpect {
            status { isUnprocessableContent() }
            header { string(PROCESSOR_REVISION_HEADER, identity.linting!!) }
        }
    }
}
