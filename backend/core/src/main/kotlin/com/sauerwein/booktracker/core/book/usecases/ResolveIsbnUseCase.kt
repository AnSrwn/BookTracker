package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.toCandidate

/**
 * Catalog first, then the metadata source. What the source resolves enters the catalog, so
 * the next scan of the same edition stays local — the catalog is the real rate limiter. The
 * answer carries the catalog row's id, so the app can add exactly that book.
 *
 * The one place an ISBN is resolved: the scan, an ISBN typed into search, and adding a hit by
 * its ISBN (9.7 D1) all go through [resolve], so they cannot drift apart.
 */
class ResolveIsbnUseCase(
    private val bookRepository: BookRepository,
    private val bookMetadataSource: BookMetadataSource,
    private val upsertCatalogEntryUseCase: UpsertCatalogEntryUseCase,
) {
    /** For the scan: the catalog book if there is one, else the source's answer as it is. */
    operator fun invoke(isbn: Isbn): BookCandidate? = when (val resolution = resolve(isbn)) {
        is Resolution.Stored -> resolution.book.toCandidate()
        is Resolution.NotStored -> resolution.candidate
        null -> null
    }

    /** For adding by ISBN: only a book that is (now) in the catalog; a record that may not be stored is `null`. */
    fun catalogBook(isbn: Isbn): Book? = (resolve(isbn) as? Resolution.Stored)?.book

    private fun resolve(isbn: Isbn): Resolution? {
        bookRepository.findCatalogByIsbn(isbn)?.let { return Resolution.Stored(it) }

        val candidate = bookMetadataSource.findByIsbn(isbn) ?: return null
        return upsertCatalogEntryUseCase(candidate)?.let(Resolution::Stored) ?: Resolution.NotStored(candidate)
    }

    private sealed interface Resolution {
        data class Stored(val book: Book) : Resolution
        data class NotStored(val candidate: BookCandidate) : Resolution
    }
}
