package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.adapter.bookdata.FakeHttp
import com.sauerwein.booktracker.adapter.bookdata.FakeHttp.Canned
import com.sauerwein.booktracker.adapter.bookdata.openLibraryRef
import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PRIDE = Isbn.of("9780141439518")
private val AUSTEN = Author("Jane Austen", openLibraryRef("OL21594A"))

private fun json(fixture: String) = Canned(200, FakeHttp.fixture(fixture))
private val NOT_FOUND = Canned(404, """{"error": "notfound"}""")

/** An edition that names no author, as Open Library returns *Pride and Prejudice* (9.10 finding). */
private val editionWithoutAuthors = json("openlibrary-isbn-no-authors.json")
private val work = json("openlibrary-work.json")
private val austen = json("openlibrary-author-austen.json")

/**
 * The client's request chain: an edition's own authors, else its work's (9.10 R9). Each test also
 * checks which requests were sent — under 1 req/s every request is scan latency.
 */
class OpenLibraryClientTest {

    private fun client(http: FakeHttp) = OpenLibraryClient(
        RestClient.builder().baseUrl("https://openlibrary.example").requestFactory(http).build(),
    )

    private fun FakeHttp.paths() = requests.map { it.uri.path }

    @Test
    fun `takes the authors from the work when the edition names none`() {
        val http = FakeHttp(editionWithoutAuthors, work, austen)
        val candidate = assertNotNull(client(http).findByIsbn(PRIDE))

        assertEquals("Pride and Prejudice", candidate.title)
        assertEquals(listOf(AUSTEN), candidate.authors)
        assertTrue(candidate.authorsFromWork)
        assertEquals(SourceRecordId("OL66554W"), candidate.workKey)
        assertEquals(SourceRecordId("OL37076991M"), candidate.sourceId)
        assertEquals(listOf("/isbn/9780141439518.json", "/works/OL66554W.json", "/authors/OL21594A.json"), http.paths())
    }

    /** Adding a search hit fetches the edition by its key: the same fallback applies (R2). */
    @Test
    fun `takes the authors from the work for a record fetched by its id, too`() {
        val http = FakeHttp(editionWithoutAuthors, work, austen)
        val candidate = assertNotNull(client(http).findByRecordId(openLibraryRef("OL37076991M")))

        assertEquals(listOf(AUSTEN), candidate.authors)
        assertTrue(candidate.authorsFromWork)
        assertEquals(listOf("/books/OL37076991M.json", "/works/OL66554W.json", "/authors/OL21594A.json"), http.paths())
    }

    @Test
    fun `never asks the work when the edition's own author resolves`() {
        val http = FakeHttp(json("openlibrary-isbn-hit.json"), json("openlibrary-author.json"))
        val candidate = assertNotNull(client(http).findByIsbn(Isbn.of("9780375826689")))

        assertEquals(listOf("Christopher Paolini"), candidate.authors.map { it.name })
        assertFalse(candidate.authorsFromWork)
        assertEquals(listOf("/isbn/9780375826689.json", "/authors/OL1425963A.json"), http.paths())
    }

    /** Q1b: a listed author that cannot be found is no author either. */
    @Test
    fun `asks the work when none of the edition's authors resolves`() {
        val edition = """{"key": "/books/OL37076991M", "title": "Pride and Prejudice",
            "authors": [{"key": "/authors/OL1A"}], "works": [{"key": "/works/OL66554W"}]}"""
        val http = FakeHttp(Canned(200, edition), NOT_FOUND, work, austen)
        val candidate = assertNotNull(client(http).findByIsbn(PRIDE))

        assertEquals(listOf(AUSTEN), candidate.authors)
        assertTrue(candidate.authorsFromWork)
        assertEquals(
            listOf("/isbn/9780141439518.json", "/authors/OL1A.json", "/works/OL66554W.json", "/authors/OL21594A.json"),
            http.paths(),
        )
    }

    /** Review R1-1: an author record whose name is blank names nobody. */
    @Test
    fun `asks the work when the edition's author has a blank name`() {
        val edition = """{"key": "/books/OL37076991M", "title": "Pride and Prejudice",
            "authors": [{"key": "/authors/OL1A"}], "works": [{"key": "/works/OL66554W"}]}"""
        val http = FakeHttp(Canned(200, edition), Canned(200, """{"name": "  "}"""), work, austen)
        val candidate = assertNotNull(client(http).findByIsbn(PRIDE))

        assertEquals(listOf(AUSTEN), candidate.authors)
        assertTrue(candidate.authorsFromWork)
        assertEquals(4, http.requests.size)
    }

    /** Review R1-10: the mapper drops it anyway, so nothing more is asked. */
    @Test
    fun `spends no author or work request on an edition without a title`() {
        val http = FakeHttp(Canned(200, """{"key": "/books/OL1M", "title": " ", "works": [{"key": "/works/OL66554W"}]}"""))

        assertNull(client(http).findByIsbn(PRIDE))
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `resolves at most two of the work's authors`() {
        val manyAuthors = """{"authors": [
            {"author": {"key": "/authors/OL1A"}}, {"author": {"key": "/authors/OL2A"}}, {"author": {"key": "/authors/OL3A"}}]}"""
        val http = FakeHttp(
            editionWithoutAuthors, Canned(200, manyAuthors), Canned(200, """{"name": "One"}"""), Canned(200, """{"name": "Two"}"""),
        )

        assertEquals(listOf("One", "Two"), client(http).findByIsbn(PRIDE)?.authors?.map { it.name })
        assertEquals(4, http.requests.size)
    }

    /** Q9: a book may have no author; the record still counts, with its cover and work key. */
    @Test
    fun `a work without authors leaves the book without one`() {
        val http = FakeHttp(editionWithoutAuthors, Canned(200, """{"key": "/works/OL66554W", "title": "x"}"""))
        val candidate = assertNotNull(client(http).findByIsbn(PRIDE))

        assertEquals(emptyList(), candidate.authors)
        assertFalse(candidate.authorsFromWork)
        assertEquals(SourceRecordId("OL66554W"), candidate.workKey)
        assertNotNull(candidate.coverUrl)
        assertEquals(2, http.requests.size)
    }

    @Test
    fun `an edition without a work has no author, after one request`() {
        val edition = """{"key": "/books/OL37076991M", "title": "Pride and Prejudice"}"""
        val http = FakeHttp(Canned(200, edition))

        assertEquals(emptyList(), client(http).findByIsbn(PRIDE)?.authors)
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `a missing work leaves the book without an author`() {
        val http = FakeHttp(editionWithoutAuthors, NOT_FOUND)
        val candidate = assertNotNull(client(http).findByIsbn(PRIDE))

        assertEquals(emptyList(), candidate.authors)
        assertFalse(candidate.authorsFromWork)
        assertEquals(listOf("/isbn/9780141439518.json", "/works/OL66554W.json"), http.paths())
    }

    /** A broken work call is an outage, like a broken author call — never a book without an author. */
    @Test
    fun `a failing work call fails the lookup`() {
        val http = FakeHttp(editionWithoutAuthors, Canned(500, "oops"))

        assertFailsWith<OpenLibraryException> { client(http).findByIsbn(PRIDE) }
    }

    @Test
    fun `a work answering with something other than JSON fails the lookup`() {
        val http = FakeHttp(editionWithoutAuthors, Canned(200, "<html>busy</html>", MediaType.TEXT_HTML))

        assertFailsWith<OpenLibraryException> { client(http).findByIsbn(PRIDE) }
    }

    /** R4: keys are third-party data; a malformed one never goes into a path. */
    @Test
    fun `never requests a malformed work or author key`() {
        val badWork = """{"key": "/books/OL37076991M", "title": "Pride and Prejudice", "works": [{"key": "/works/../admin"}]}"""
        val badAuthor = """{"authors": [{"author": {"key": "/authors/../admin"}}]}"""

        val noWork = FakeHttp(Canned(200, badWork))
        assertEquals(emptyList(), client(noWork).findByIsbn(PRIDE)?.authors)
        assertEquals(1, noWork.requests.size)

        val noAuthor = FakeHttp(editionWithoutAuthors, Canned(200, badAuthor))
        assertEquals(emptyList(), client(noAuthor).findByIsbn(PRIDE)?.authors)
        assertEquals(2, noAuthor.requests.size)
    }
}
