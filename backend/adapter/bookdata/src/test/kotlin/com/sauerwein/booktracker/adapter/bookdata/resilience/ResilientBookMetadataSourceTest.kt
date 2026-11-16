package com.sauerwein.booktracker.adapter.bookdata.resilience

import com.sauerwein.booktracker.adapter.bookdata.StubBookMetadataSource
import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceQuotaExhaustedException
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.web.client.ResourceAccessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private val ERAGON = Isbn.of("9783570402931")
private val UNKNOWN = Isbn.of("9780375826689")

class ResilientBookMetadataSourceTest {

    private val meterRegistry = SimpleMeterRegistry()
    private val circuitBreaker = CircuitBreaker.of("test", BookDataResilienceConfig.circuitBreakerConfig())

    private fun resilient(delegate: BookMetadataSource) =
        ResilientBookMetadataSource(delegate, circuitBreaker, meterRegistry)

    private fun count(operation: String, outcome: String): Long =
        meterRegistry.find("bookdata.source.requests")
            .tags("source", "stub", "operation", operation, "outcome", outcome)
            .timer()?.count() ?: 0

    @Test
    fun `passes results through and counts hits and misses per operation`() {
        val source = resilient(StubBookMetadataSource())

        assertEquals("Eragon – Das Vermächtnis der Drachenreiter", source.findByIsbn(ERAGON)?.title)
        assertEquals(null, source.findByIsbn(UNKNOWN))
        assertEquals(1, source.search("eragon").size)
        assertEquals(emptyList(), source.search("tschick"))

        assertEquals(1, count("isbn", "hit"))
        assertEquals(1, count("isbn", "miss"))
        assertEquals(1, count("search", "hit"))
        assertEquals(1, count("search", "miss"))
        assertEquals(StubBookMetadataSource().descriptor, source.descriptor)
    }

    @Test
    fun `turns a provider failure into SourceUnavailableException`() {
        val source = resilient(FailingSource(TestSourceException()))

        val e = assertFailsWith<SourceUnavailableException> { source.findByIsbn(ERAGON) }

        assertEquals(SourceId.of("stub"), e.source)
        assertIs<TestSourceException>(e.cause)
        assertEquals(1, count("isbn", "error"))
    }

    @Test
    fun `turns a timeout or IO error into SourceUnavailableException`() {
        val source = resilient(FailingSource(ResourceAccessException("Read timed out")))

        assertFailsWith<SourceUnavailableException> { source.search("eragon") }
        assertEquals(1, count("search", "error"))
    }

    @Test
    fun `opens the circuit after repeated failures and stops calling the source`() {
        val delegate = FailingSource(TestSourceException())
        val source = resilient(delegate)

        repeat(5) { assertFailsWith<SourceUnavailableException> { source.findByIsbn(ERAGON) } }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)

        assertFailsWith<SourceUnavailableException> { source.findByIsbn(ERAGON) }
        assertEquals(5, delegate.calls)
        assertEquals(1, count("isbn", "circuit_open"))
    }

    @Test
    fun `reports throttling without opening the circuit`() {
        val source = resilient(FailingSource(RequestNotPermitted.createRequestNotPermitted(RateLimiter.ofDefaults("test"))))

        repeat(10) { assertFailsWith<SourceUnavailableException> { source.search("eragon") } }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertEquals(10, count("search", "throttled"))
    }

    /** A bug in a mapper is not an outage and must stay a 500. */
    @Test
    fun `does not disguise an unexpected exception as an outage`() {
        val source = resilient(FailingSource(IllegalStateException("bug")))

        assertFailsWith<IllegalStateException> { source.findByIsbn(ERAGON) }
        assertEquals(1, count("isbn", "error"))
    }

    /** 9.7 D4: a spent quota is reported as such, so the composite can skip the provider instead of failing. */
    @Test
    fun `reports a spent quota as its own outcome without opening the circuit`() {
        val delegate = FailingSource(QuotaExhaustedException("quota spent"))
        val source = resilient(delegate)

        repeat(10) {
            val e = assertFailsWith<SourceQuotaExhaustedException> { source.search("eragon") }
            assertEquals(SourceId.of("stub"), e.source)
        }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertEquals(10, count("search", "quota_exhausted"))
        assertEquals(0, count("search", "error"))
    }

    /** Adding a search hit fetches the full record: an outage there must be a 503 too, tagged as its own operation. */
    @Test
    fun `reports an outage on a record lookup as unavailable`() {
        val source = resilient(FailingSource(TestSourceException()))

        assertFailsWith<SourceUnavailableException> { source.findByRecordId(SourceRef(SourceId.of("stub"), SourceRecordId("1055026991"))) }
        assertEquals(1, count("record", "error"))
    }

    private class TestSourceException : BookSourceException("source answered 503")

    private class FailingSource(private val failure: RuntimeException) : BookMetadataSource {
        var calls = 0
        override val descriptor = StubBookMetadataSource().descriptor

        override fun search(text: String): List<BookCandidate> {
            calls++
            throw failure
        }

        override fun findByIsbn(isbn: Isbn): BookCandidate? {
            calls++
            throw failure
        }

        override fun findByRecordId(ref: SourceRef): BookCandidate? {
            calls++
            throw failure
        }
    }
}
