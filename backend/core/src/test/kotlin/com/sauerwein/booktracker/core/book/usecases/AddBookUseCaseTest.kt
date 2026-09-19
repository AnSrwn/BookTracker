package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.models.Isbn
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

class AddBookUseCaseTest {

    private val bookRepository = InMemoryBookRepository()
    private val clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00.123456789Z"), ZoneOffset.UTC)
    private val addBookUseCase = AddBookUseCase(bookRepository, clock)

    private val command = AddBookCommand(
        title = "Dune",
        author = "Frank Herbert",
        publisher = "Ace",
        isbn = Isbn.of("978-0-441-17271-9"),
        publicationYear = 1965,
        pageCount = 896,
        coverUrl = "https://example.com/dune.jpg",
    )

    @Test
    fun `saves the book and returns it`() {
        val book = addBookUseCase(command)

        assertEquals(book, bookRepository.findById(book.id))
    }

    @Test
    fun `copies all fields from the command`() {
        val book = addBookUseCase(command)

        assertEquals("Dune", book.title)
        assertEquals("Frank Herbert", book.author)
        assertEquals("Ace", book.publisher)
        assertEquals("9780441172719", book.isbn?.value)
        assertEquals(1965, book.publicationYear)
        assertEquals(896, book.pageCount)
        assertEquals("https://example.com/dune.jpg", book.coverUrl)
    }

    @Test
    fun `sets createdAt and updatedAt to the current time, truncated to microseconds`() {
        val book = addBookUseCase(command)

        val expected = Instant.parse("2026-01-01T10:00:00.123456Z")
        assertEquals(expected, book.createdAt)
        assertEquals(expected, book.updatedAt)
    }

    @Test
    fun `generates a new id for each book`() {
        val first = addBookUseCase(command)
        val second = addBookUseCase(command)

        assertNotEquals(first.id, second.id)
        assertEquals(2, bookRepository.findAll().size)
    }

    @Test
    fun `rejects a blank title and saves nothing`() {
        assertFailsWith<IllegalArgumentException> {
            addBookUseCase(command.copy(title = " "))
        }

        assertTrue(bookRepository.findAll().isEmpty())
    }
}
