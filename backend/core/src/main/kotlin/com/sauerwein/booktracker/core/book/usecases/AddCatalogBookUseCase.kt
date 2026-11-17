package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRef
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
 * A search hit that is not stored yet enters the catalog here, and only here (D27). It is resolved
 * by its ISBN through the scan's own [ResolveIsbnUseCase], so the stored row is the same merged
 * CC0 record a scan of that ISBN stores (9.9 Q10) — and never the hit itself, whose data may not
 * be storable at all (9.7 D1, D6). Only if the ISBN resolves to nothing — or the resolution fails
 * because a provider is down (9.9 Q13) — is the hit's own source record, where it carries one,
 * fetched as the full record and stored instead (D29). No storable record → not found.
 *
 * If the sources are down, the add fails with `SourceUnavailableException`.
 */
class AddCatalogBookUseCase(
    private val bookRepository: BookRepository,
    private val bookMetadataSource: BookMetadataSource,
    private val storeCatalogEntryUseCase: StoreCatalogEntryUseCase,
    private val resolveIsbnUseCase: ResolveIsbnUseCase,
    private val currentUserProvider: CurrentUserProvider,
    private val clock: Clock,
) {
    operator fun invoke(ref: CatalogBookRef): Book {
        val user = currentUserProvider.currentUserId()
        val book = when (ref) {
            is CatalogBookRef.Stored -> bookRepository.findVisible(ref.id, user)?.takeIf { it.ownerId == null }
            is CatalogBookRef.ByIsbn -> resolve(ref)
        } ?: throw NotFoundException("Catalog book", ref.describe())

        bookRepository.addToLibrary(
            user = user,
            bookId = book.id,
            at = Instant.now(clock).truncatedTo(ChronoUnit.MICROS),
        )

        return book
    }

    /**
     * The ISBN like a scan; the hit's own record when that finds nothing — and also when it fails
     * because a provider is down (9.9 Q13): the record comes from the source the user picked the
     * hit from, and a scan during that outage would store no more. Only if the record yields
     * nothing either does the outage stand.
     *
     * No fallback on an interrupted request — its I/O would fail and count against a healthy
     * source (9.9 review F15) — nor when the hit's own source is among those that just failed:
     * asking it again would only wait a second time (N17).
     */
    private fun resolve(ref: CatalogBookRef.ByIsbn): Book? {
        val resolved = try {
            resolveIsbnUseCase.catalogBook(ref.isbn)
        } catch (e: SourceUnavailableException) {
            val record = ref.record ?: throw e
            if (Thread.currentThread().isInterrupted || record.source in e.failedSources()) throw e
            return storedOrFromSource(record) ?: throw e
        }
        return resolved ?: ref.record?.let(::storedOrFromSource)
    }

    private fun SourceUnavailableException.failedSources(): Set<SourceId> =
        (listOf(this) + suppressed.filterIsInstance<SourceUnavailableException>()).map { it.source }.toSet()

    private fun storedOrFromSource(record: SourceRef): Book? =
        bookRepository.findCatalogBySource(record.source, record.recordId) ?: storeFromSource(record)

    private fun storeFromSource(record: SourceRef): Book? {
        // The source may answer with another record id (a merged edition); its data is still the source's own
        val candidate = bookMetadataSource.findByRecordId(record)
            ?.takeIf { it.isAddable && it.source == record.source }
            ?: return null
        return storeCatalogEntryUseCase(candidate)
    }

    /** The app never learns which source a hit came from, not even from an error message. */
    private fun CatalogBookRef.describe(): Any = when (this) {
        is CatalogBookRef.Stored -> id.value
        is CatalogBookRef.ByIsbn -> "from search"
    }
}
