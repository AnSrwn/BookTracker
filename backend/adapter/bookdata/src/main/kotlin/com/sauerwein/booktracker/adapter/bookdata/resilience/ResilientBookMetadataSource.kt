package com.sauerwein.booktracker.adapter.bookdata.resilience

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceQuotaExhaustedException
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceRef
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClientException

private const val METRIC_SOURCE_REQUESTS = "bookdata.source.requests"

private const val OPERATION_SEARCH = "search"
private const val OPERATION_ISBN = "isbn"
private const val OPERATION_RECORD = "record"

private const val OUTCOME_HIT = "hit"
private const val OUTCOME_MISS = "miss"
private const val OUTCOME_ERROR = "error"
private const val OUTCOME_CIRCUIT_OPEN = "circuit_open"
private const val OUTCOME_THROTTLED = "throttled"
private const val OUTCOME_QUOTA_EXHAUSTED = "quota_exhausted"

/**
 * Wraps any source with a circuit breaker and a per-source timer, both named after
 * the descriptor id, and turns a failing source into [SourceUnavailableException].
 *
 * Only failures of the source itself are translated. Anything else is a bug and
 * stays a 500 instead of being reported as an outage.
 */
class ResilientBookMetadataSource(
    private val delegate: BookMetadataSource,
    private val circuitBreaker: CircuitBreaker,
    private val meterRegistry: MeterRegistry,
) : BookMetadataSource {

    private val log = LoggerFactory.getLogger(javaClass)

    override val descriptor = delegate.descriptor

    override fun search(text: String): List<BookCandidate> =
        call(OPERATION_SEARCH, isHit = { it.isNotEmpty() }) { delegate.search(text) }

    override fun findByIsbn(isbn: Isbn): BookCandidate? =
        call(OPERATION_ISBN, isHit = { it != null }) { delegate.findByIsbn(isbn) }

    override fun findByRecordId(ref: SourceRef): BookCandidate? =
        call(OPERATION_RECORD, isHit = { it != null }) { delegate.findByRecordId(ref) }

    private fun <T> call(operation: String, isHit: (T) -> Boolean, block: () -> T): T {
        val sample = Timer.start(meterRegistry)
        var outcome = OUTCOME_ERROR

        try {
            // Boxed: executeSupplier is declared non-null, and a miss is a null result
            val result = circuitBreaker.executeSupplier { Holder(block()) }.value
            outcome = if (isHit(result)) OUTCOME_HIT else OUTCOME_MISS
            return result
        } catch (e: CallNotPermittedException) {
            outcome = OUTCOME_CIRCUIT_OPEN
            throw unavailable(operation, e)
        } catch (e: RequestNotPermitted) {
            outcome = OUTCOME_THROTTLED
            throw unavailable(operation, e)
        } catch (e: QuotaExhaustedException) {
            // Not an outage: the provider was not asked, and the composite skips it (D4)
            outcome = OUTCOME_QUOTA_EXHAUSTED
            throw SourceQuotaExhaustedException(descriptor.id, e)
        } catch (e: BookSourceException) {
            throw unavailable(operation, e)
        } catch (e: RestClientException) {
            throw unavailable(operation, e)
        } finally {
            sample.stop(timer(operation, outcome))
        }
    }

    private fun unavailable(operation: String, cause: Exception): SourceUnavailableException {
        log.warn("Book source {} failed on {}: {}", descriptor.id, operation, cause.toString())
        return SourceUnavailableException(descriptor.id, cause)
    }

    private fun timer(operation: String, outcome: String): Timer =
        Timer.builder(METRIC_SOURCE_REQUESTS)
            .tag("source", descriptor.id.value)
            .tag("operation", operation)
            .tag("outcome", outcome)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)

    private class Holder<T>(val value: T)
}
