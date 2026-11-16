package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.testBook
import com.sauerwein.booktracker.core.user.UserId
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class GetBooksUseCaseTest {

    private val userA = UserId(UUID.randomUUID())
    private val userB = UserId(UUID.randomUUID())

    private val bookRepository = InMemoryBookRepository()
    private val getBooksUseCase = GetBooksUseCase(bookRepository = bookRepository) { userA }

    @Test
    fun `returns own private books and the catalog books the user added`() {
        val own = testBook(ownerId = userA, title = "Dune")
        val added = testBook(ownerId = null, title = "Neuromancer")
        bookRepository.addPrivateBook(own)
        bookRepository.seed(added)
        bookRepository.addToLibrary(userA, added.id, Instant.EPOCH)

        assertEquals(setOf(own, added), getBooksUseCase().toSet())
    }

    /** The bug this step fixed: "visible" used to mean "in my books", so every scan showed up for everyone. */
    @Test
    fun `does not list catalog books the user never added`() {
        val scannedByB = testBook(ownerId = null, title = "Eragon")
        bookRepository.seed(scannedByB)
        bookRepository.addToLibrary(userB, scannedByB.id, Instant.EPOCH)
        bookRepository.seed(testBook(ownerId = null, title = "Neuromancer"))

        assertEquals(emptyList(), getBooksUseCase())
    }

    @Test
    fun `does not list another user's private books`() {
        bookRepository.addPrivateBook(testBook(ownerId = userB))

        assertEquals(emptyList(), getBooksUseCase())
    }

    @Test
    fun `lists the most recently added book first`() {
        val older = testBook(ownerId = null, title = "Older")
        val newer = testBook(ownerId = null, title = "Newer")
        bookRepository.seed(older, newer)
        bookRepository.addToLibrary(userA, older.id, Instant.parse("2026-01-01T00:00:00Z"))
        bookRepository.addToLibrary(userA, newer.id, Instant.parse("2026-02-01T00:00:00Z"))

        assertEquals(listOf("Newer", "Older"), getBooksUseCase().map { it.title })
    }
}
