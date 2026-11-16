package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.user.CurrentUserProvider
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

data class CreatePrivateBookCommand(
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn: Isbn?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
)

/**
 * A book only its owner sees — entered by hand, or prefilled by the app from a catalog book
 * the user wanted to change. Catalog books themselves are never edited (D15). `manual` here
 * means "data the user owns", not strictly "typed in". Private books are independent of each
 * other, so the same ISBN may exist several times.
 */
class CreatePrivateBookUseCase(
    private val bookRepository: BookRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val clock: Clock,
) {
    operator fun invoke(command: CreatePrivateBookCommand): Book {
        val now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)

        val book = Book(
            id = BookId(UUID.randomUUID()),
            ownerId = currentUserProvider.currentUserId(),
            title = command.title,
            authors = command.authors.map { Author(name = it, ref = null) },
            publisher = command.publisher,
            isbn = command.isbn,
            publicationYear = command.publicationYear,
            pageCount = command.pageCount,
            coverUrl = command.coverUrl,
            source = SourceId.MANUAL,
            sourceId = null,
            sourceFetchedAt = null,
            workKey = null,
            createdAt = now,
            updatedAt = now,
        )

        bookRepository.addPrivateBook(book)

        return book
    }
}
