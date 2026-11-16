package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.isCatalogEligible
import com.sauerwein.booktracker.core.book.models.toCatalogBook
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/** Grows the catalog from a resolved candidate. Returns `null`, writing nothing, when the licence forbids it. */
class CatalogUpsertUseCase(
    private val bookRepository: BookRepository,
    private val clock: Clock,
) {
    operator fun invoke(candidate: BookCandidate): Book? {
        if (!candidate.isCatalogEligible) return null

        val now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)
        return bookRepository.upsertCatalogEntry(candidate.toCatalogBook(id = BookId(UUID.randomUUID()), now = now))
    }
}
