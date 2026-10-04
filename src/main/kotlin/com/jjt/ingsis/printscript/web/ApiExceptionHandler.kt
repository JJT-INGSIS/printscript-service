package com.jjt.ingsis.printscript.web

import com.jjt.ingsis.printscript.language.UnsupportedVersionException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(UnsupportedVersionException::class)
    fun unsupportedVersion(exception: UnsupportedVersionException): ProblemDetail =
        ProblemDetail
            .forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "PrintScript version '${exception.requested}' is not supported.",
            ).apply {
                title = "Unsupported version"
                setProperty("supportedVersions", exception.supported)
            }

    @ExceptionHandler(Exception::class)
    fun technicalFailure(exception: Exception): ProblemDetail {
        logger.error("The request failed for a technical reason", exception)

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The request could not be completed because of a technical failure.",
        )
    }
}
