package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceQuotaExhaustedException
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.*

/**
 * All providers behind the one port. Which provider serves which query comes from its
 * descriptor, in which order from configuration — this class must never name a source.
 *
 * A provider that fails is skipped and the others still answer. Only when nothing was found
 * and at least one asked provider failed is the result an outage rather than a miss
 * (9.7 D4, D5). A provider whose quota is spent was not asked, so it does not count as
 * failed: otherwise every miss would be a 503 until the quota resets.
 *
 * Sequential for now; parallel fan-out and the field merge are 9.9.
 */
class CompositeBookMetadataSource(
    private val sources: List<BookMetadataSource>,
    searchOrder: List<SourceId>,
    isbnOrder: List<SourceId>,
) : BookMetadataSource {

    init {
        val duplicates = sources.groupBy { it.descriptor.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Book sources registered twice: $duplicates" }
    }

    val searchProviders: List<BookMetadataSource> =
        ordered(sources.filter { it.descriptor.supportsFreeTextSearch }, searchOrder)

    val isbnProviders: List<BookMetadataSource> =
        ordered(sources.filter { it.descriptor.supportsIsbnLookup }, isbnOrder)

    override val descriptor = SourceDescriptor(
        id = COMPOSITE_ID,
        supportsIsbnLookup = isbnProviders.isNotEmpty(),
        supportsFreeTextSearch = searchProviders.isNotEmpty(),
        providesCovers = sources.any { it.descriptor.providesCovers },
        // Not a source of records: each candidate carries its own provider's licence bit
        persistableInPublicCatalog = false,
    )

    override fun search(text: String): List<BookCandidate> {
        val failures = mutableListOf<SourceUnavailableException>()
        val hits = searchProviders.flatMap { provider -> ask(failures) { provider.search(text) }.orEmpty() }

        if (hits.isEmpty() && failures.isNotEmpty()) throw combined(failures)
        return hits
    }

    /** The first provider that knows the ISBN wins, and the rest are not asked. */
    override fun findByIsbn(isbn: Isbn): BookCandidate? {
        val failures = mutableListOf<SourceUnavailableException>()
        for (provider in isbnProviders) {
            ask(failures) { provider.findByIsbn(isbn) }?.let { return it }
        }

        if (failures.isNotEmpty()) throw combined(failures)
        return null
    }

    /** Only the provider that issued the record can fetch it; one that is not running is a miss. */
    override fun findByRecordId(ref: SourceRef): BookCandidate? =
        sources.firstOrNull { it.descriptor.id == ref.source }?.findByRecordId(ref)

    private inline fun <T> ask(failures: MutableList<SourceUnavailableException>, block: () -> T): T? =
        try {
            block()
        } catch (_: SourceQuotaExhaustedException) {
            null
        } catch (e: SourceUnavailableException) {
            failures += e
            null
        }

    private fun combined(failures: List<SourceUnavailableException>): SourceUnavailableException =
        failures.first().apply { failures.drop(1).forEach(::addSuppressed) }

    companion object {
        val COMPOSITE_ID = SourceId.of("composite")

        /** Listed ids first, in their order; the rest after, by id, so the result never depends on bean order. */
        private fun ordered(providers: List<BookMetadataSource>, order: List<SourceId>): List<BookMetadataSource> {
            val rank = order.withIndex().associate { (index, id) -> id to index }
            return providers.sortedWith(
                compareBy<BookMetadataSource> { rank[it.descriptor.id] ?: Int.MAX_VALUE }
                    .thenBy { it.descriptor.id.value },
            )
        }
    }
}
