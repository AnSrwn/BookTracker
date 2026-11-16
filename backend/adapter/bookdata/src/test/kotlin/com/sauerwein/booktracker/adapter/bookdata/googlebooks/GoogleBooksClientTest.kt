package com.sauerwein.booktracker.adapter.bookdata.googlebooks

import com.sauerwein.booktracker.adapter.bookdata.FakeHttp
import com.sauerwein.booktracker.adapter.bookdata.FakeHttp.Canned
import com.sauerwein.booktracker.adapter.bookdata.resilience.QuotaExhaustedException
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val KEY = "test-key-not-real"

private val DAILY_429 = Canned(
    429,
    """{"error":{"code":429,"message":"Quota exceeded for quota metric 'Queries' and limit 'Queries per day' of service 'books.googleapis.com'","status":"RESOURCE_EXHAUSTED","errors":[{"reason":"rateLimitExceeded"}]}}""",
)
private val DAILY_403 = Canned(403, """{"error":{"code":403,"message":"Daily Limit Exceeded","errors":[{"reason":"dailyLimitExceeded"}]}}""")
private val LOCATION_403 = Canned(
    403,
    """{"error":{"code":403,"message":"Cannot determine user location for geographically restricted operation.","errors":[{"reason":"unknownLocation"}]}}""",
)
private val UNAVAILABLE = Canned(503, """{"error":{"code":503,"message":"Service temporarily unavailable."}}""")

class GoogleBooksClientTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private val guard = DailyQuotaGuard(clock)

    private fun client(http: FakeHttp) = GoogleBooksClient(
        restClient = googleBooksRestClient("https://books.example", "BookTracker/test", KEY, http),
        country = "DE",
        quotaGuard = guard,
        retryBackoff = Duration.ZERO,
    )

    private fun searchFixture() = Canned(200, FakeHttp.fixture("google-books-search.json"))

    @Test
    fun `maps a real search response`() {
        val hits = client(FakeHttp(searchFixture())).search("eragon paolini")

        assertEquals(10, hits.size)
        assertTrue(hits.all { it.source == SourceId.of("google_books") && !it.persistableInPublicCatalog })
        assertEquals(Isbn.of("9783641023041"), hits.first { it.title == "Eragon - Die Weisheit des Feuers" }.isbn)
    }

    @Test
    fun `sends the key in a header, never in the URL, and asks for books in Germany`() {
        val http = FakeHttp(searchFixture())

        client(http).search("eragon paolini")

        val request = http.requests.single()
        assertEquals(KEY, request.headers.getFirst("X-Goog-Api-Key"))
        assertFalse(KEY in request.uri.toString())
        val query = request.uri.rawQuery
        assertTrue("country=DE" in query, query)
        assertTrue("printType=books" in query, query)
        assertTrue("q=eragon%20paolini" in query, query)
        assertTrue(request.uri.rawPath.endsWith("/books/v1/volumes"))
    }

    /** E3: a 503 is common at Google and worth retrying. */
    @Test
    fun `retries a 503 and answers once Google does`() {
        val http = FakeHttp(UNAVAILABLE, UNAVAILABLE, searchFixture())

        assertEquals(10, client(http).search("eragon").size)
        assertEquals(3, http.requests.size)
    }

    @Test
    fun `gives up after three 503s`() {
        val http = FakeHttp(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE)

        assertFailsWith<GoogleBooksException> { client(http).search("eragon") }
        assertEquals(3, http.requests.size)
    }

    /** E2, E4: a spent quota is never retried, and nothing is sent until it resets. */
    @Test
    fun `never retries a 429 and stops asking until the quota resets`() {
        val http = FakeHttp(DAILY_429)
        val client = client(http)

        assertFailsWith<QuotaExhaustedException> { client.search("eragon") }
        assertFailsWith<QuotaExhaustedException> { client.search("eldest") }

        assertEquals(1, http.requests.size)
        assertTrue(guard.isClosed())
    }

    @Test
    fun `treats a quota 403 like a 429`() {
        val http = FakeHttp(DAILY_403)

        assertFailsWith<QuotaExhaustedException> { client(http).search("eragon") }
        assertEquals(1, http.requests.size)
        assertTrue(guard.isClosed())
    }

    /** Without `country` a server Google cannot place gets this 403 — a failure, not a spent quota. */
    @Test
    fun `reports any other 403 as a failure of the source`() {
        val e = assertFailsWith<GoogleBooksException> { client(FakeHttp(LOCATION_403)).search("eragon") }

        assertTrue("Cannot determine user location" in e.message.orEmpty())
        assertFalse(guard.isClosed())
    }

    @Test
    fun `reports an unexpected status as a failure of the source`() {
        assertFailsWith<GoogleBooksException> { client(FakeHttp(Canned(500))).search("eragon") }
    }

    @Test
    fun `an empty answer is a miss`() {
        assertEquals(emptyList(), client(FakeHttp(Canned(200, "{}"))).search("zzzz"))
    }

    /** D7: the descriptor keeps the scan path away from Google; calling it anyway is a bug. */
    @Test
    fun `does no ISBN lookup and never fetches a record to store`() {
        val client = client(FakeHttp())

        assertFalse(client.descriptor.supportsIsbnLookup)
        assertFailsWith<UnsupportedOperationException> { client.findByIsbn(Isbn.of("9783570402931")) }
        assertNull(client.findByRecordId(SourceRef(SourceId.of("google_books"), SourceRecordId("_tiYRR30oe0C"))))
    }
}
