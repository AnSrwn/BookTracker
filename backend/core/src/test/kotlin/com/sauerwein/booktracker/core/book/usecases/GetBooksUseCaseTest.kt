package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.testBook
import com.sauerwein.booktracker.core.user.UserId
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class GetBooksUseCaseTest {

    private val userA = UserId(UUID.randomUUID())
    private val userB = UserId(UUID.randomUUID())

    private val bookRepository = InMemoryBookRepository()
    private val getBooksUseCase = GetBooksUseCase(bookRepository = bookRepository) { userA }

    @Test
    fun `returns own books and public ones, but not those of another user`() {
        val own = testBook(ownerId = userA, title = "Dune")
        val public = testBook(ownerId = null, title = "Neuromancer")
        val foreign = testBook(ownerId = userB, title = "Snow Crash")
        listOf(own, public, foreign).forEach(bookRepository::add)

        assertEquals(setOf(own, public), getBooksUseCase().toSet())
    }

    @Test
    fun `returns an empty list when the user has no books and the catalog is empty`() {
        bookRepository.add(testBook(ownerId = userB))

        assertEquals(emptyList(), getBooksUseCase())
    }
}
