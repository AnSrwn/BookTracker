package com.sauerwein.booktracker.adapter.web.common.exception

import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.shared.exception.ConflictException
import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import com.sauerwein.booktracker.core.shared.exception.ValidationException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private const val DETAIL_VALIDATION_FAILED = "Request validation failed"

private const val PROBLEM_PROPERTY_ERRORS = "errors"
private const val PROBLEM_PROPERTY_CODE = "code"

private const val CODE_SOURCE_UNAVAILABLE = "SourceUnavailable"

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
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, DETAIL_VALIDATION_FAILED)
        problem.setProperty(
            PROBLEM_PROPERTY_ERRORS,
            e.bindingResult.fieldErrors.map {
                ValidationError(
                    field = it.field,
                    code = it.code,
                    message = it.defaultMessage
                )
            },
        )
        return problem
    }

    @ExceptionHandler(ValidationException::class)
    fun handleDomainValidation(e: ValidationException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, DETAIL_VALIDATION_FAILED)
        problem.setProperty(PROBLEM_PROPERTY_ERRORS, listOf(ValidationError(field = e.field, code = e.code, message = e.message)))
        return problem
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(e: ConflictException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.message)
        problem.setProperty(PROBLEM_PROPERTY_CODE, e.code)
        return problem
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(e: NotFoundException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message)
    }

    /** An outage is not a miss: a 404 here would send the app to the manual form for a book that exists. */
    @ExceptionHandler(SourceUnavailableException::class)
    fun handleSourceUnavailable(e: SourceUnavailableException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.message)
        problem.setProperty(PROBLEM_PROPERTY_CODE, CODE_SOURCE_UNAVAILABLE)
        return problem
    }
}
