package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import com.sauerwein.booktracker.core.user.CurrentUserProvider
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Adds a shared catalog book to the caller's books — a link, never a copy, so nothing anyone
 * else sees can change. Only approved catalog books qualify: a private book (the caller's own
 * or another user's) is not found here, exactly like an unknown id.
 */
class AddCatalogBookUseCase(
    private val bookRepository: BookRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val clock: Clock,
) {
    operator fun invoke(id: BookId): Book {
        val user = currentUserProvider.currentUserId()
        val book = bookRepository.findVisible(id, user)
            ?.takeIf { it.ownerId == null }
            ?: throw NotFoundException("Catalog book", id.value)

        bookRepository.addToLibrary(
            user = user,
            bookId = book.id,
            at = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
        )

        return book
    }
}
