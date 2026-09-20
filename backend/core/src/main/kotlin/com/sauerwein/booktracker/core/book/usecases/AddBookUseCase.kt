package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.user.CurrentUserProvider
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

data class AddBookCommand(
    val title: String,
    val author: String,
    val publisher: String?,
    val isbn: Isbn?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
)

class AddBookUseCase(
    private val bookRepository: BookRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val clock: Clock,
) {
    operator fun invoke(command: AddBookCommand): Book {
        val now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)

        val book = Book(
            id = BookId(UUID.randomUUID()),
            ownerId = currentUserProvider.currentUserId(),
            title = command.title,
            author = command.author,
            publisher = command.publisher,
            isbn = command.isbn,
            publicationYear = command.publicationYear,
            pageCount = command.pageCount,
            coverUrl = command.coverUrl,
            createdAt = now,
            updatedAt = now,
        )

        bookRepository.add(book)

        return book
    }
}
