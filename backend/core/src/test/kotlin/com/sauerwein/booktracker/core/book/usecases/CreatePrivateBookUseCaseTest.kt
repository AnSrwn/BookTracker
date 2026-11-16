package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.models.BookKind
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.kind
import com.sauerwein.booktracker.core.user.UserId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlin.test.*

class CreatePrivateBookUseCaseTest {

    private val userId = UserId(UUID.randomUUID())
    private val bookRepository = InMemoryBookRepository()
    private val clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00.123456789Z"), ZoneOffset.UTC)
    private val createPrivateBook =
        CreatePrivateBookUseCase(bookRepository = bookRepository, currentUserProvider = { userId }, clock = clock)

    private val command = CreatePrivateBookCommand(
        title = "Dune",
        authors = listOf("Frank Herbert", "Brian Herbert"),
        publisher = "Ace",
        isbn = Isbn.of("978-0-441-17271-9"),
        publicationYear = 1965,
        pageCount = 896,
        coverUrl = "https://example.com/dune.jpg",
    )

    @Test
    fun `saves the book and returns it`() {
        val book = createPrivateBook(command)

        assertEquals(book, bookRepository.findVisible(book.id, userId))
    }

    @Test
    fun `puts the new book into the owner's books`() {
        val book = createPrivateBook(command)

        assertEquals(listOf(userId to book.id), bookRepository.links())
        assertEquals(listOf(book), bookRepository.findLibrary(userId))
    }

    @Test
    fun `is a private book`() {
        assertEquals(BookKind.PRIVATE, createPrivateBook(command).kind)
    }

    /** Private books are independent entities (D14): a second one with the same ISBN is a new book, not a conflict. */
    @Test
    fun `accepts a second private book with the same ISBN`() {
        val first = createPrivateBook(command)
        val second = createPrivateBook(command.copy(title = "Dune, annotated"))

        assertNotEquals(first.id, second.id)
        assertEquals(setOf(first, second), bookRepository.findLibrary(userId).toSet())
    }

    @Test
    fun `copies all fields from the command`() {
        val book = createPrivateBook(command)

        assertEquals("Dune", book.title)
        assertEquals(listOf("Frank Herbert", "Brian Herbert"), book.authors.map { it.name })
        assertEquals("Ace", book.publisher)
        assertEquals("9780441172719", book.isbn?.value)
        assertEquals(1965, book.publicationYear)
        assertEquals(896, book.pageCount)
        assertEquals("https://example.com/dune.jpg", book.coverUrl)
    }

    @Test
    fun `marks a book entered by hand as manual, with no source record`() {
        val book = createPrivateBook(command)

        assertEquals(SourceId.MANUAL, book.source)
        assertNull(book.sourceId)
        assertNull(book.sourceFetchedAt)
    }

    @Test
    fun `sets the owner to the current user`() {
        val book = createPrivateBook(command)

        assertEquals(userId, book.ownerId)
    }

    @Test
    fun `sets createdAt and updatedAt to the current time, truncated to microseconds`() {
        val book = createPrivateBook(command)

        val expected = Instant.parse("2026-01-01T10:00:00.123456Z")
        assertEquals(expected, book.createdAt)
        assertEquals(expected, book.updatedAt)
    }

    @Test
    fun `generates a new id for each book`() {
        val first = createPrivateBook(command)
        val second = createPrivateBook(command)

        assertNotEquals(first.id, second.id)
        assertEquals(2, bookRepository.stored().size)
    }

    @Test
    fun `rejects a blank title and saves nothing`() {
        assertFailsWith<IllegalArgumentException> {
            createPrivateBook(command.copy(title = " "))
        }

        assertTrue(bookRepository.stored().isEmpty())
    }
}
