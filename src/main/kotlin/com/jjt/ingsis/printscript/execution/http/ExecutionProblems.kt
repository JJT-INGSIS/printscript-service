package com.jjt.ingsis.printscript.execution.http

import com.jjt.ingsis.printscript.execution.ExecutionResult
import com.jjt.ingsis.printscript.execution.Rejection
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

internal class ExecutionHttpFailure(
    val rejection: Rejection,
    val reason: String,
) : RuntimeException(reason)

internal fun invalidRequest(): Nothing = throw ExecutionHttpFailure(Rejection.INVALID, "invalid-request")

internal fun <T> ExecutionResult<T>.value(): T =
    when (this) {
        is ExecutionResult.Success -> value
        is ExecutionResult.Rejected -> throw ExecutionHttpFailure(status, reason)
    }

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ExecutionProblems {
    @ExceptionHandler(ExecutionHttpFailure::class)
    internal fun rejected(failure: ExecutionHttpFailure): ProblemDetail {
        val status =
            when (failure.rejection) {
                Rejection.INVALID -> 400
                Rejection.MISSING -> 404
                Rejection.CONFLICT -> 409
                Rejection.CAPACITY -> 429
                Rejection.UNSUPPORTED -> 422
            }
        return ProblemDetail.forStatus(status).apply {
            type = URI.create("urn:printscript:problem:${failure.reason}")
            title = failure.reason
            detail = "The execution request could not be completed."
            if (failure.rejection == Rejection.UNSUPPORTED) setProperty("supportedVersions", listOf("1.0", "1.1"))
        }
    }
}
