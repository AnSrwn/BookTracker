package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.*
import com.sauerwein.booktracker.core.shared.exception.ConflictException
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Grows the catalog from a resolved candidate and returns the catalog row that now stands for
 * it. Returns `null`, writing nothing, when the licence forbids it — or when another row holds
 * the ISBN but is not readable (not approved): the lookup must not fail over a side effect.
 */
class UpsertCatalogEntryUseCase(
    private val bookRepository: BookRepository,
    private val clock: Clock,
) {
    operator fun invoke(candidate: BookCandidate): Book? {
        if (!candidate.isCatalogEligible) return null

        val now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS)

        return try {
            bookRepository.upsertCatalogEntry(
                book = candidate.toCatalogBook(id = BookId(UUID.randomUUID()), now = now)
            )
        } catch (_: ConflictException) {
            // Another record already holds this ISBN in the catalog. That row is the catalog's
            // answer for the edition — return it, id and data together, never a mix of both.
            candidate.isbn?.let(bookRepository::findCatalogByIsbn)
        }
    }
}
