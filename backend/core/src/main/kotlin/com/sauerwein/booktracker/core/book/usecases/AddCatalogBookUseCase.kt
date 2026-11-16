package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.isAddable
import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import com.sauerwein.booktracker.core.user.CurrentUserProvider
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Adds a shared catalog book to the caller's books — a link, never a copy, so nothing anyone
 * else sees can change. Only approved catalog books qualify: a private book (the caller's own
 * or another user's) is not found here, exactly like an unknown id.
 *
 * A search hit that is not stored yet enters the catalog here, and only here (D27): the full
 * record is fetched from its source first (D29), so the catalog never holds a thin search row.
 * If the source is down, the add fails with `SourceUnavailableException`.
 */
class AddCatalogBookUseCase(
    private val bookRepository: BookRepository,
    private val bookMetadataSource: BookMetadataSource,
    private val upsertCatalogEntryUseCase: UpsertCatalogEntryUseCase,
    private val currentUserProvider: CurrentUserProvider,
    private val clock: Clock,
) {
    operator fun invoke(ref: CatalogBookRef): Book {
        val user = currentUserProvider.currentUserId()
        val book = when (ref) {
            is CatalogBookRef.Stored -> bookRepository.findVisible(ref.id, user)?.takeIf { it.ownerId == null }
            is CatalogBookRef.SourceRecord -> bookRepository.findCatalogBySource(ref.source, ref.recordId) ?: storeFromSource(ref)
        } ?: throw NotFoundException("Catalog book", ref.describe())

        bookRepository.addToLibrary(
            user = user,
            bookId = book.id,
            at = Instant.now(clock).truncatedTo(ChronoUnit.MICROS),
        )

        return book
    }

    private fun storeFromSource(ref: CatalogBookRef.SourceRecord): Book? {
        if (ref.source != bookMetadataSource.descriptor.id) return null

        // The source may answer with another record id (a merged edition); its data is still the source's own
        val candidate = bookMetadataSource.findByRecordId(ref.recordId)
            ?.takeIf { it.isAddable && it.source == ref.source }
            ?: return null
        return upsertCatalogEntryUseCase(candidate)
    }

    /** The app never learns which source a hit came from, not even from an error message. */
    private fun CatalogBookRef.describe(): Any = when (this) {
        is CatalogBookRef.Stored -> id.value
        is CatalogBookRef.SourceRecord -> "from search"
    }
}
