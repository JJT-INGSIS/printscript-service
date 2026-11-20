package com.jjt.ingsis.printscript.processor

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

class ProcessorRevisionFilter(
    private val identity: ProcessorIdentity,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val revision = identity.revisionFor(request.requestURI)
        if (revision != null) response.setHeader(PROCESSOR_REVISION_HEADER, revision)
        chain.doFilter(request, response)
    }
}
