package com.jjt.ingsis.printscript.validation

import com.jjt.ingsis.printscript.language.LanguageVersion
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(ValidationController::class)
@Import(ValidationTechnicalFailureTest.FailingValidation::class)
class ValidationTechnicalFailureTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `a technical failure is a server error and never a verdict about the code`() {
        val example = ContractDocument.examples.single { documented -> documented.status == SERVER_ERROR }

        mockMvc
            .post("/validate") {
                contentType = MediaType.APPLICATION_JSON
                content = example.request
            }.andExpect {
                status { isInternalServerError() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                content { json(example.response, JsonCompareMode.STRICT) }
                jsonPath("$.valid") { doesNotExist() }
                jsonPath("$.diagnostics") { doesNotExist() }
            }
    }

    @TestConfiguration
    class FailingValidation {
        @Bean
        fun validationService(): ValidationService =
            object : ValidationService() {
                override fun validate(
                    code: String,
                    version: LanguageVersion,
                ): ValidationResponse = error("Simulated technical failure")
            }
    }

    private companion object {
        const val SERVER_ERROR = 500
    }
}
