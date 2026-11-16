package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.testBook
import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import com.sauerwein.booktracker.core.user.UserId
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GetBookUseCaseTest {

    private val userA = UserId(UUID.randomUUID())
    private val userB = UserId(UUID.randomUUID())

    private val bookRepository = InMemoryBookRepository()
    private val getBookUseCase = GetBookUseCase(bookRepository = bookRepository) { userA }

    @Test
    fun `returns an own book`() {
        val book = testBook(ownerId = userA)
        bookRepository.addPrivateBook(book)

        assertEquals(book, getBookUseCase(book.id))
    }

    @Test
    fun `returns a catalog book even when the user never added it`() {
        val book = testBook(ownerId = null)
        bookRepository.seed(book)

        assertEquals(book, getBookUseCase(book.id))
    }

    @Test
    fun `reports another user's book as not found instead of forbidden`() {
        val book = testBook(ownerId = userB)
        bookRepository.addPrivateBook(book)

        assertFailsWith<NotFoundException> { getBookUseCase(book.id) }
    }

    @Test
    fun `reports an unknown id as not found`() {
        assertFailsWith<NotFoundException> { getBookUseCase(BookId(UUID.randomUUID())) }
    }
}
