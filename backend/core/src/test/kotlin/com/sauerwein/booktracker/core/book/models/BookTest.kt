package com.sauerwein.booktracker.core.book.models

import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BookTest {

    private fun book(coverUrl: String?) = Book(
        id = BookId(UUID.randomUUID()),
        title = "Dune",
        author = "Frank Herbert",
        publisher = null,
        isbn = null,
        publicationYear = null,
        pageCount = null,
        coverUrl = coverUrl,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `accepts an https cover URL`() {
        assertEquals("https://example.com/dune.jpg", book("https://example.com/dune.jpg").coverUrl)
    }

    @Test
    fun `accepts an http cover URL`() {
        assertEquals("http://books.google.com/cover?id=1", book("http://books.google.com/cover?id=1").coverUrl)
    }

    @Test
    fun `accepts no cover URL`() {
        assertEquals(null, book(null).coverUrl)
    }

    @Test
    fun `rejects a javascript cover URL`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            book("javascript:alert(1)")
        }

        assertContains(exception.message.orEmpty(), "http(s)")
    }

    @Test
    fun `rejects a data cover URL`() {
        assertFailsWith<IllegalArgumentException> {
            book("data:text/html,<script>alert(1)</script>")
        }
    }

    @Test
    fun `rejects a relative cover URL`() {
        assertFailsWith<IllegalArgumentException> {
            book("/covers/dune.jpg")
        }
    }

    @Test
    fun `rejects an http URL without host`() {
        assertFailsWith<IllegalArgumentException> {
            book("https:///dune.jpg")
        }
    }

    @Test
    fun `rejects a malformed cover URL`() {
        assertFailsWith<IllegalArgumentException> {
            book("https://exa mple.com/dune.jpg")
        }
    }
}
