package com.jjt.ingsis.printscript.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class HealthcheckTransactionFilterTest {
    private fun ignoredFor(uri: String): Boolean {
        var ignored = false
        val chain = MockFilterChain()

        HealthcheckTransactionFilter { ignored = true }
            .doFilter(MockHttpServletRequest("GET", uri), MockHttpServletResponse(), chain)

        assertThat(chain.request).describedAs(uri).isNotNull()
        return ignored
    }

    @Test
    fun `the healthcheck probe is not recorded but still answered`() {
        assertThat(ignoredFor("/")).isTrue()
    }

    @Test
    fun `every other request keeps its transaction`() {
        listOf("/validate", "/linting/rules", "/formatting").forEach { uri ->
            assertThat(ignoredFor(uri)).describedAs(uri).isFalse()
        }
    }
}
