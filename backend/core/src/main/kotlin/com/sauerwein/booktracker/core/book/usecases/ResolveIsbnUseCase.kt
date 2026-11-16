package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.toCandidate

/**
 * Catalog first, then the metadata source. What the source resolves enters the catalog, so
 * the next scan of the same edition stays local — the catalog is the real rate limiter. The
 * answer carries the catalog row's id, so the app can add exactly that book.
 */
class ResolveIsbnUseCase(
    private val bookRepository: BookRepository,
    private val bookMetadataSource: BookMetadataSource,
    private val upsertCatalogEntryUseCase: UpsertCatalogEntryUseCase,
) {
    operator fun invoke(isbn: Isbn): BookCandidate? {
        bookRepository.findCatalogByIsbn(isbn)?.let { return it.toCandidate() }

        val candidate = bookMetadataSource.findByIsbn(isbn) ?: return null
        return upsertCatalogEntryUseCase(candidate)?.toCandidate() ?: candidate
    }
}
