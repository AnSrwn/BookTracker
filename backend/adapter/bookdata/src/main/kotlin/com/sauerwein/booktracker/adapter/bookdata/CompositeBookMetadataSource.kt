package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceQuotaExhaustedException
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRef

/**
 * All providers behind the one port. Which provider serves which query comes from its
 * descriptor, in which order from configuration — this class must never name a source.
 *
 * A provider that fails is skipped and the others still answer. A miss is only a miss if
 * someone actually looked: when nothing was found, the result is an outage if any asked
 * provider failed (9.7 D4, D5), or if no provider answered at all — every one skipped for a
 * spent quota (D11). A spent quota alone does not turn a miss into an outage while another
 * provider answered cleanly: otherwise every miss would be a 503 until the quota resets.
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
        val outcome = Outcome()
        val hits = searchProviders.flatMap { provider -> outcome.ask { provider.search(text) }.orEmpty() }

        if (hits.isEmpty()) outcome.throwIfNobodyLooked()
        return hits
    }

    /** The first provider that knows the ISBN wins, and the rest are not asked. */
    override fun findByIsbn(isbn: Isbn): BookCandidate? {
        val outcome = Outcome()
        for (provider in isbnProviders) {
            outcome.ask { provider.findByIsbn(isbn) }?.let { return it }
        }

        outcome.throwIfNobodyLooked()
        return null
    }

    /** Only the provider that issued the record can fetch it; one that is not running is a miss. */
    override fun findByRecordId(ref: SourceRef): BookCandidate? =
        sources.firstOrNull { it.descriptor.id == ref.source }?.findByRecordId(ref)

    /** What the asked providers did: answered cleanly (a hit or a miss), failed, or were skipped. */
    private class Outcome {
        private var answered = 0
        private val failures = mutableListOf<SourceUnavailableException>()
        private val skipped = mutableListOf<SourceQuotaExhaustedException>()

        fun <T> ask(block: () -> T): T? =
            try {
                block().also { answered++ }
            } catch (e: SourceQuotaExhaustedException) {
                skipped += e
                null
            } catch (e: SourceUnavailableException) {
                failures += e
                null
            }

        /**
         * Called when nothing was found. A failure means the book may exist (D4, D5); no clean
         * answer at all means nobody looked (D11). With no provider asked, a miss stays a miss:
         * that is the catalog-only setup, warned about at startup.
         */
        fun throwIfNobodyLooked() {
            when {
                failures.isNotEmpty() -> throw combined(failures + skipped)
                skipped.isNotEmpty() && answered == 0 -> throw combined(skipped)
            }
        }

        private fun combined(all: List<SourceUnavailableException>): SourceUnavailableException =
            all.first().apply { all.drop(1).forEach(::addSuppressed) }
    }

    companion object {
        val COMPOSITE_ID = SourceId.of("composite")

        /**
         * Ids in a configured order that match no running provider. Not an error: a disabled
         * provider may stay listed. But a misspelled one would silently fall to the end of the
         * order, so it is worth a warning at startup.
         */
        fun unmatchedOrderIds(order: List<SourceId>, sources: List<BookMetadataSource>): List<SourceId> {
            val running = sources.map { it.descriptor.id }.toSet()
            return order.filterNot { it in running }
        }

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
