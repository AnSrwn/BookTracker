package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.toCandidate
import com.sauerwein.booktracker.core.shared.exception.ConflictException

/**
 * Catalog first, then the metadata source. What the source resolves enters the catalog, so
 * the next scan of the same edition stays local — the catalog is the real rate limiter.
 */
class ResolveIsbnUseCase(
    private val bookRepository: BookRepository,
    private val bookMetadataSource: BookMetadataSource,
    private val catalogUpsertUseCase: CatalogUpsertUseCase,
) {
    operator fun invoke(isbn: Isbn): BookCandidate? {
        bookRepository.findCatalogByIsbn(isbn)?.let { return it.toCandidate() }

        val candidate = bookMetadataSource.findByIsbn(isbn) ?: return null
        try {
            catalogUpsertUseCase(candidate)
        } catch (e: ConflictException) {
            // Another source's row claimed this ISBN meanwhile. The lookup itself succeeded;
            // the catalog write is a side effect and must not fail the scan.
        }
        return candidate
    }
}
