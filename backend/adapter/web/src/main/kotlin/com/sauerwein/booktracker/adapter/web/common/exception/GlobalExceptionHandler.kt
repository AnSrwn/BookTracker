package com.sauerwein.booktracker.adapter.web.common.exception

import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates expected errors into ProblemDetail responses.
 *
 * Highest precedence on purpose: Spring Boot registers its own
 * ProblemDetailsExceptionHandler with @Order(0), which would otherwise answer
 * validation errors first with a generic "Invalid request content.".
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class GlobalExceptionHandler {

    /** Bean Validation on the request DTOs. Lists the failing fields, which Spring's default response does not. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed")
        problem.setProperty(
            "errors",
            e.bindingResult.fieldErrors.map { ValidationError(it.field, it.code, it.defaultMessage) },
        )
        return problem
    }

    /**
     * Rules that only core can check, e.g. the ISBN checksum in Isbn.of().
     *
     * TODO Use a domain exception instead: IllegalArgumentException may also come
     * from a library or a bug, and its message would then leak internals as a 400.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message)
    }

    /** Thrown by core when a requested aggregate does not exist. */
    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message)
    }
}
