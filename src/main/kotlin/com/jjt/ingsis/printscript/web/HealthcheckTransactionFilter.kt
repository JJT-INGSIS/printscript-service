package com.jjt.ingsis.printscript.web

import com.newrelic.api.agent.NewRelic
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val HEALTHCHECK_PATH = "/"

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class HealthcheckTransactionFilter(
    private val ignoreTransaction: () -> Unit = NewRelic::ignoreTransaction,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (request.requestURI == HEALTHCHECK_PATH) {
            ignoreTransaction()
        }
        chain.doFilter(request, response)
    }
}
