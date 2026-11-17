package com.sauerwein.booktracker.adapter.bookdata.dnb

import com.sauerwein.booktracker.adapter.bookdata.FakeHttp
import com.sauerwein.booktracker.adapter.bookdata.FakeHttp.Canned
import com.sauerwein.booktracker.adapter.bookdata.openLibraryRef
import com.sauerwein.booktracker.adapter.bookdata.resilience.ResilientBookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val XML = MediaType("text", "xml", Charsets.UTF_8)
private val ERAGON_ISBN = Isbn.of("9783570402931")

private fun dnbRef(id: String) = SourceRef(SourceId.of("dnb"), SourceRecordId(id))

class DnbSruClientTest {

    private fun client(http: FakeHttp) = DnbSruClient(
        RestClient.builder()
            .baseUrl("https://dnb.example")
            .defaultHeader(HttpHeaders.USER_AGENT, "BookTracker/test")
            .requestFactory(http)
            .build(),
    )

    private fun eragon() = Canned(200, FakeHttp.fixture("dnb-sru-isbn.xml"), XML)

    @Test
    fun `looks up an ISBN with an encoded CQL query, MARC21-XML and a small record limit`() {
        val http = FakeHttp(eragon())

        val candidate = client(http).findByIsbn(ERAGON_ISBN)

        assertEquals("Eragon – Das Vermächtnis der Drachenreiter", candidate?.title)
        val request = http.requests.single()
        assertEquals("/sru/dnb", request.uri.path)
        val query = request.uri.rawQuery
        assertTrue("query=isbn%3D9783570402931" in query, query)
        assertTrue("recordSchema=MARC21-xml" in query, query)
        assertTrue("recordPacking=xml" in query, query)
        assertTrue("maximumRecords=5" in query, query)
        assertEquals("BookTracker/test", request.headers.getFirst(HttpHeaders.USER_AGENT))
    }

    @Test
    fun `a lookup without records is a miss`() {
        assertNull(client(FakeHttp(Canned(200, sruResponse(), XML))).findByIsbn(ERAGON_ISBN))
    }

    @Test
    fun `takes the first record that maps, skipping an e-book edition with the same ISBN`() {
        val records = FakeHttp.fixture("dnb-sru-tschick.xml")
        val ebook = Isbn.of("9783963082085")

        // The e-book record carries the asked ISBN but is dropped; no other record carries it
        assertNull(client(FakeHttp(Canned(200, records, XML))).findByIsbn(ebook))
        assertEquals(
            "Tschick",
            client(FakeHttp(Canned(200, records, XML))).findByIsbn(Isbn.of("9783733509200"))?.title,
        )
    }

    @Test
    fun `never searches`() {
        val http = FakeHttp()

        assertTrue(client(http).search("eragon").isEmpty())
        assertTrue(http.requests.isEmpty())
        assertTrue(!DnbSruClient.DESCRIPTOR.supportsFreeTextSearch)
    }

    // Failures are failures, never misses (R13)

    @Test
    fun `a server error is a failure`() {
        assertFailsWith<DnbException> { client(FakeHttp(Canned(500, "oops", XML))).findByIsbn(ERAGON_ISBN) }
    }

    @Test
    fun `a 404 is a failure too, since SRU answers a miss with 200 and no records`() {
        assertFailsWith<DnbException> { client(FakeHttp(Canned(404, "", MediaType.TEXT_HTML))).findByIsbn(ERAGON_ISBN) }
    }

    @Test
    fun `an answer that is not XML is a failure`() {
        assertFailsWith<DnbException> {
            client(FakeHttp(Canned(200, "<html>maintenance</html>", MediaType.TEXT_HTML))).findByIsbn(ERAGON_ISBN)
        }
    }

    /** E8: the resilience wrapper turns it into an outage, so the composite asks the next provider. */
    @Test
    fun `a failure reaches the composite as an outage`() {
        val resilient = ResilientBookMetadataSource(
            delegate = client(FakeHttp(Canned(500, "oops", XML))),
            circuitBreaker = CircuitBreaker.ofDefaults("dnb"),
            meterRegistry = SimpleMeterRegistry(),
        )

        assertFailsWith<SourceUnavailableException> { resilient.findByIsbn(ERAGON_ISBN) }
    }

    // Records (E6)

    @Test
    fun `fetches a record by its IDN`() {
        val http = FakeHttp(eragon())

        val candidate = client(http).findByRecordId(dnbRef("1055026991"))

        assertEquals(SourceRecordId("1055026991"), candidate?.sourceId)
        val query = http.requests.single().uri.rawQuery
        assertTrue("query=idn%3D1055026991" in query, query)
        assertTrue("recordPacking=xml" in query, query)
        assertTrue("maximumRecords=1" in query, query)
    }

    @Test
    fun `does not hand out another record than the one asked for`() {
        assertNull(client(FakeHttp(eragon())).findByRecordId(dnbRef("1234567890")))
    }

    @Test
    fun `sends no request for another source's record or a malformed id`() {
        val http = FakeHttp()

        assertNull(client(http).findByRecordId(openLibraryRef("OL7353617M")))
        assertNull(client(http).findByRecordId(dnbRef("1055026991 or isbn=1")))
        assertNull(client(http).findByRecordId(dnbRef("12")))
        assertTrue(http.requests.isEmpty())
    }
}
