package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.adapter.bookdata.fixture
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private const val USER_AGENT = "BookTracker/test (+test@example.org)"

class OpenLibraryClientTest {

    private val builder = OpenLibraryConfig.restClientBuilder(USER_AGENT)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = OpenLibraryClient(builder.build())

    @Test
    fun `maps an ISBN hit and resolves the author with a second call`() {
        server.expect(requestTo("https://openlibrary.org/isbn/9780375826689.json"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
            .andRespond(withSuccess(fixture("openlibrary-isbn-hit.json"), MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://openlibrary.org/authors/OL1425963A.json"))
            .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
            .andRespond(withSuccess(fixture("openlibrary-author.json"), MediaType.APPLICATION_JSON))

        val candidate = client.findByIsbn(Isbn.of("9780375826689"))!!

        server.verify()
        assertEquals("Eragon", candidate.title)
        assertEquals("Christopher Paolini", candidate.author)
        assertEquals("Alfred A. Knopf", candidate.publisher)
        assertEquals(Isbn.of("9780375826689"), candidate.isbn)
        assertEquals(2003, candidate.publicationYear)
        assertEquals(509, candidate.pageCount)
        assertEquals("https://covers.openlibrary.org/b/isbn/9780375826689-L.jpg?default=false", candidate.coverUrl)
        assertEquals(SourceId.of("open_library"), candidate.source)
        assertEquals(SourceRecordId("OL3685343M"), candidate.sourceId)
        assertEquals(true, candidate.persistableInPublicCatalog)
    }

    /** The miss body is an HTML page; reading it as JSON would turn a plain miss into a 500. */
    @Test
    fun `returns null on a 404 with an HTML body and makes no author call`() {
        server.expect(requestTo("https://openlibrary.org/isbn/9783570402931.json"))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(fixture("openlibrary-isbn-miss.html"))
            )

        assertNull(client.findByIsbn(Isbn.of("9783570402931")))
        server.verify()
    }

    @Test
    fun `returns null when the author cannot be resolved`() {
        server.expect(requestTo("https://openlibrary.org/isbn/9780375826689.json"))
            .andRespond(withSuccess(fixture("openlibrary-isbn-hit.json"), MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://openlibrary.org/authors/OL1425963A.json"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).body("<html></html>"))

        assertNull(client.findByIsbn(Isbn.of("9780375826689")))
        server.verify()
    }

    @Test
    fun `throws on a server error instead of reporting a miss`() {
        server.expect(requestTo("https://openlibrary.org/isbn/9780375826689.json"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        assertFailsWith<OpenLibraryException> { client.findByIsbn(Isbn.of("9780375826689")) }
    }

    @Test
    fun `throws on a 200 that is not JSON`() {
        server.expect(requestTo("https://openlibrary.org/isbn/9780375826689.json"))
            .andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML))

        assertFailsWith<OpenLibraryException> { client.findByIsbn(Isbn.of("9780375826689")) }
    }

    @Test
    fun `maps search hits as works`() {
        server.expect(path("/search.json"))
            .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
            .andRespond(withSuccess(fixture("openlibrary-search.json"), MediaType.APPLICATION_JSON))

        val candidates = client.search("eragon")

        assertEquals(listOf("Eragon", "Brisingr", "Eragon & Eldest", "Eldest", "Inheritance"), candidates.map { it.title })
        assertEquals(setOf("Christopher Paolini"), candidates.map { it.author }.toSet())

        val eragon = candidates[0]
        assertNull(eragon.sourceId)
        assertNull(eragon.isbn)
        assertNull(eragon.pageCount)
        assertEquals(2000, eragon.publicationYear)
        assertEquals("https://covers.openlibrary.org/b/id/13921600-L.jpg?default=false", eragon.coverUrl)

        assertEquals(SourceRecordId("OL10751961M"), candidates[1].sourceId)
    }

    @Test
    fun `encodes the search text and restricts the response fields`() {
        server.expect(path("/search.json"))
            .andExpect(query("q", "Vermächtnis der Drachen & mehr"))
            .andExpect(query("limit", "10"))
            .andExpect(query("fields", "title,author_name,first_publish_year,cover_i,cover_edition_key"))
            .andRespond(withSuccess("""{"docs":[]}""", MediaType.APPLICATION_JSON))

        assertEquals(emptyList(), client.search("Vermächtnis der Drachen & mehr"))
        server.verify()
    }

    private fun path(expected: String) = RequestMatcher { request ->
        assertEquals(expected, request.uri.path)
    }

    private fun query(name: String, expected: String) = RequestMatcher { request ->
        val values = request.uri.rawQuery.split("&")
            .map { it.split("=", limit = 2) }
            .filter { URLDecoder.decode(it[0], Charsets.UTF_8) == name }
            .map { URLDecoder.decode(it[1], Charsets.UTF_8) }
        assertEquals(listOf(expected), values)
    }
}
