package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.adapter.bookdata.FakeHttp.Canned
import com.sauerwein.booktracker.adapter.bookdata.dnb.DnbSruClient
import com.sauerwein.booktracker.adapter.bookdata.dnb.sruResponse
import com.sauerwein.booktracker.adapter.bookdata.googlebooks.DailyQuotaGuard
import com.sauerwein.booktracker.adapter.bookdata.googlebooks.GoogleBooksClient
import com.sauerwein.booktracker.adapter.bookdata.googlebooks.googleBooksRestClient
import com.sauerwein.booktracker.adapter.bookdata.openlibrary.OpenLibraryClient
import com.sauerwein.booktracker.adapter.bookdata.resilience.ResilientBookMetadataSource
import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.text.Normalizer
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The port contract every provider must keep, checked against its own recorded fixtures (9.9 R7).
 * Adding a provider is a checklist: subclass this, describe the provider and its fixtures, and
 * every rule below runs against it. A capability the descriptor denies is not asked for.
 *
 * Each provider runs wrapped like in production ([ResilientBookMetadataSource]), so "a failure is
 * a [SourceUnavailableException], never a miss" is checked end to end.
 */
abstract class BookMetadataSourceContract {

    /** The provider under test, talking to [http] instead of the network. */
    protected abstract fun provider(http: FakeHttp): BookMetadataSource

    protected abstract val descriptor: SourceDescriptor

    /** A real search response. `null` only for a provider that does not search. */
    protected open val searchHit: List<Canned>? = null

    /** What the provider answers for a search that finds nothing. */
    protected open val searchMiss: List<Canned>? = null

    /** A real lookup of [isbn]. `null` only for a provider that does no ISBN lookup. */
    protected open val isbnHit: Pair<Isbn, List<Canned>>? = null

    /** What the provider answers for an ISBN it does not know. */
    protected open val isbnMiss: List<Canned>? = null

    /** A real fetch of a record by its id, e.g. a search hit being added. */
    protected open val recordHit: Pair<SourceRecordId, List<Canned>>? = null

    /** What the provider's server answers when it is broken. */
    protected open val serverError: Canned = Canned(500, "oops", MediaType.TEXT_PLAIN)

    private fun wrapped(vararg responses: Canned) = ResilientBookMetadataSource(
        delegate = provider(FakeHttp(*responses)),
        circuitBreaker = CircuitBreaker.ofDefaults(descriptor.id.value),
        meterRegistry = SimpleMeterRegistry(),
    )

    // What every candidate must look like

    private fun assertConforms(candidate: BookCandidate) {
        val where = "${descriptor.id}: '${candidate.title}'"
        assertEquals(descriptor.id, candidate.source, where)
        assertEquals(descriptor.persistableInPublicCatalog, candidate.persistableInPublicCatalog, where)
        assertTrue(candidate.title.isNotBlank(), where)
        assertTrue(candidate.authors.isNotEmpty() && candidate.authors.all { it.name.isNotBlank() }, where)
        candidate.authors.forEach { author -> author.ref?.let { assertEquals(descriptor.id, it.source, "$where, author ${author.name}") } }
        candidate.pageCount?.let { assertTrue(it > 0, "$where: page count $it") }
        candidate.coverUrl?.let { assertTrue(it.startsWith("https://"), "$where: cover $it") }
        if (!descriptor.providesCovers) assertNull(candidate.coverUrl, where)
        candidate.language?.let { assertEquals(it, Language.of(it.value), "$where: language $it") }
        assertNull(candidate.bookId, where)
        assertNull(candidate.kind, where)

        val strings = listOfNotNull(candidate.title, candidate.publisher, candidate.sourceId?.value) +
            candidate.authors.map { it.name }
        strings.forEach { value ->
            assertTrue(value.none(Char::isISOControl), "$where: control character in '$value'")
            assertTrue(Normalizer.isNormalized(value, Normalizer.Form.NFC), "$where: not NFC: '$value'")
            assertEquals(value.trim(), value, "$where: untrimmed '$value'")
        }
    }

    @Test
    fun `the descriptor id is a valid source id`() {
        assertEquals(descriptor.id, SourceId.of(descriptor.id.value))
        assertEquals(descriptor, provider(FakeHttp()).descriptor)
    }

    // Search

    @Test
    fun `search hits keep the contract`() {
        val responses = searchHit ?: return
        val hits = wrapped(*responses.toTypedArray()).search("eragon")

        assertTrue(hits.isNotEmpty(), "${descriptor.id}: the search fixture should yield hits")
        hits.forEach(::assertConforms)
    }

    /**
     * The composite never asks for a capability the descriptor denies. If asked anyway, a provider
     * answers nothing or refuses outright — but never sends a request for it.
     */
    private fun <T> deniedCapability(call: (BookMetadataSource) -> T, isNothing: (T) -> Boolean) {
        val http = FakeHttp()
        val answer = try {
            call(provider(http))
        } catch (_: UnsupportedOperationException) {
            null
        }

        assertTrue(answer == null || isNothing(answer), "${descriptor.id} answered a capability it denies: $answer")
        assertTrue(http.requests.isEmpty(), "${descriptor.id} sent a request for a capability it denies")
    }

    @Test
    fun `a search that finds nothing is an empty list`() {
        val responses = searchMiss ?: return

        assertEquals(emptyList(), wrapped(*responses.toTypedArray()).search("zzzzqqq"))
    }

    @Test
    fun `a provider that does not search answers nothing and asks nobody`() {
        if (descriptor.supportsFreeTextSearch) return
        deniedCapability({ it.search("eragon") }, { it.isEmpty() })
    }

    @Test
    fun `a broken server is an outage of the search, never a miss`() {
        if (!descriptor.supportsFreeTextSearch) return

        assertFailsWith<SourceUnavailableException> { wrapped(serverError, serverError, serverError).search("eragon") }
    }

    // ISBN lookup

    /**
     * The answer carries a valid ISBN — not necessarily the asked one: a record may list several
     * (an Open Library edition, hardcover and paperback). The composite sets the asked one, and a
     * stored row keeps the ISBN it has (9.9 review F1).
     */
    @Test
    fun `an ISBN lookup answers with an ISBN and keeps the contract`() {
        val (isbn, responses) = isbnHit ?: return
        val candidate = assertNotNull(wrapped(*responses.toTypedArray()).findByIsbn(isbn))

        assertConforms(candidate)
        assertNotNull(candidate.isbn)
    }

    @Test
    fun `an unknown ISBN is a miss`() {
        val responses = isbnMiss ?: return

        assertNull(wrapped(*responses.toTypedArray()).findByIsbn(Isbn.of("9780000000002")))
    }

    @Test
    fun `a provider that does no ISBN lookup answers nothing and asks nobody`() {
        if (descriptor.supportsIsbnLookup) return
        deniedCapability({ it.findByIsbn(Isbn.of("9783570402931")) }, { false })
    }

    @Test
    fun `a broken server is an outage of the ISBN lookup, never a miss`() {
        if (!descriptor.supportsIsbnLookup) return

        assertFailsWith<SourceUnavailableException> {
            wrapped(serverError, serverError, serverError).findByIsbn(Isbn.of("9783570402931"))
        }
    }

    // Records

    @Test
    fun `a record fetched by its id keeps the contract`() {
        val (id, responses) = recordHit ?: return
        val candidate = assertNotNull(wrapped(*responses.toTypedArray()).findByRecordId(SourceRef(descriptor.id, id)))

        assertConforms(candidate)
    }

    /** The composite routes by the ref's source; a provider must not answer for another one's record. */
    @Test
    fun `a record of another source is a miss without a request`() {
        val http = FakeHttp()

        assertNull(provider(http).findByRecordId(SourceRef(SourceId.of("someone_else"), SourceRecordId("OL1M"))))
        assertTrue(http.requests.isEmpty())
    }
}

private const val USER_AGENT = "BookTracker/contract-test"

private fun plainRestClient(http: FakeHttp): RestClient = RestClient.builder()
    .baseUrl("https://provider.example")
    .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
    .requestFactory(http)
    .build()

private fun json(fixture: String) = Canned(200, FakeHttp.fixture(fixture))
private val XML = MediaType("text", "xml", Charsets.UTF_8)

class OpenLibraryContractTest : BookMetadataSourceContract() {
    override fun provider(http: FakeHttp) = OpenLibraryClient(plainRestClient(http))
    override val descriptor = OpenLibraryClient.DESCRIPTOR

    override val searchHit = listOf(json("openlibrary-search.json"))
    override val searchMiss = listOf(Canned(200, """{"numFound":0,"start":0,"docs":[]}"""))

    // Edition, then its author: one lookup is two requests
    override val isbnHit = Isbn.of("9780375826689") to listOf(json("openlibrary-isbn-hit.json"), json("openlibrary-author.json"))
    override val isbnMiss = listOf(Canned(404, FakeHttp.fixture("openlibrary-isbn-miss.html"), MediaType.TEXT_HTML))
    override val recordHit = SourceRecordId("OL3685343M") to listOf(json("openlibrary-isbn-hit.json"), json("openlibrary-author.json"))
}

class GoogleBooksContractTest : BookMetadataSourceContract() {
    override fun provider(http: FakeHttp) = GoogleBooksClient(
        restClient = googleBooksRestClient("https://books.example", USER_AGENT, "test-key-not-real", http),
        country = "DE",
        quotaGuard = DailyQuotaGuard(java.time.Clock.systemUTC()),
        retryBackoff = Duration.ZERO,
    )

    override val descriptor = GoogleBooksClient.DESCRIPTOR

    override val searchHit = listOf(json("google-books-search.json"))

    // What Google answers when nothing matches: no `items` at all
    override val searchMiss = listOf(json("google-books-isbn.json"))
}

class DnbContractTest : BookMetadataSourceContract() {
    override fun provider(http: FakeHttp) = DnbSruClient(plainRestClient(http))
    override val descriptor = DnbSruClient.DESCRIPTOR

    override val isbnHit = Isbn.of("9783570402931") to listOf(Canned(200, FakeHttp.fixture("dnb-sru-isbn.xml"), XML))
    override val isbnMiss = listOf(Canned(200, sruResponse(), XML))
    override val recordHit = SourceRecordId("1055026991") to listOf(Canned(200, FakeHttp.fixture("dnb-sru-isbn.xml"), XML))
}
