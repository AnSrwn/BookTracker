package com.sauerwein.booktracker.adapter.web.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Last resort: turns anything unhandled into a 500 ProblemDetail instead of
 * Spring's default error body.
 *
 * Lives in its own advice with the lowest precedence, because a catch-all in
 * GlobalExceptionHandler would shadow every other handler, including Spring's.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
class FallbackExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Logs the stack trace, since a handled exception is logged nowhere else, and keeps internals out of the response. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception, request: HttpServletRequest): ProblemDetail {
        log.error("Unhandled exception for {} {}", request.method, request.requestURI, e)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
    }
}
