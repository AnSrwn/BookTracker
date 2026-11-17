package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceQuotaExhaustedException
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * All providers behind the one port. Which provider serves which query comes from its
 * descriptor, which value wins a field from configuration ([MergePrecedence]) — this class must
 * never name a source.
 *
 * Every provider that can serve a query is asked, in parallel on virtual threads, so a slow one
 * never holds up a fast one (architecture §7). What they return about one edition — one ISBN —
 * is merged field by field ([BookCandidateMerge]): a scan stores the merged record (9.9 Q1), and
 * a search shows one hit per ISBN. Hits without an ISBN are never merged (9.9 Q5).
 *
 * A provider that fails is skipped and the others still answer. A miss is only a miss if
 * someone actually looked: when nothing was found, the result is an outage if any asked
 * provider failed (9.7 D4, D5), or if no provider answered at all — every one skipped for a
 * spent quota (D11). A spent quota alone does not turn a miss into an outage while another
 * provider answered cleanly: otherwise every miss would be a 503 until the quota resets.
 */
class CompositeBookMetadataSource(
    private val sources: List<BookMetadataSource>,
    searchOrder: List<SourceId>,
    private val merge: BookCandidateMerge,
) : BookMetadataSource {

    init {
        val duplicates = sources.groupBy { it.descriptor.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Book sources registered twice: $duplicates" }
    }

    /** In the order their hits are listed; which value wins a field is the merge's business. */
    val searchProviders: List<BookMetadataSource> =
        ordered(sources.filter { it.descriptor.supportsFreeTextSearch }, searchOrder)

    /** All asked at once, so their order means nothing; by id, so it never depends on bean order. */
    val isbnProviders: List<BookMetadataSource> =
        ordered(sources.filter { it.descriptor.supportsIsbnLookup }, emptyList())

    override val descriptor = SourceDescriptor(
        id = COMPOSITE_ID,
        supportsIsbnLookup = isbnProviders.isNotEmpty(),
        supportsFreeTextSearch = searchProviders.isNotEmpty(),
        providesCovers = sources.any { it.descriptor.providesCovers },
        // Not a source of records: each candidate carries its own licence bit
        persistableInPublicCatalog = false,
    )

    /**
     * Hits in provider order, each ISBN once, merged from every provider that returned it and
     * placed where it first appeared — also two hits of one provider, which are the same edition.
     * Several editions of one work stay several hits (ADR-008).
     */
    override fun search(text: String): List<BookCandidate> {
        val answers = fanOut(searchProviders) { it.search(text) }
        val hits = mergedByIsbn(answers.flatMap { it.valueOrNull().orEmpty() })

        if (hits.isEmpty()) Outcome(answers).throwIfNobodyLooked()
        return hits
    }

    /**
     * Every provider that can look up ISBNs is asked; what they know is merged into one record.
     *
     * An interrupted request answers with an outage, never with what arrived so far: the merge is
     * stored for good, so a partial one would leave the row short (9.9 review F4) — and on a
     * virtual request thread the store itself would fail on the interrupt.
     */
    override fun findByIsbn(isbn: Isbn): BookCandidate? {
        val answers = fanOut(isbnProviders) { it.findByIsbn(isbn) }
        if (Thread.currentThread().isInterrupted) throw Outcome(answers).interrupted()
        val found = answers.mapNotNull { it.valueOrNull() }

        if (found.isEmpty()) {
            Outcome(answers).throwIfNobodyLooked()
            return null
        }
        return merge.merge(candidates = found, isbn = isbn)
    }

    /** Only the provider that issued the record can fetch it; one that is not running is a miss. */
    override fun findByRecordId(ref: SourceRef): BookCandidate? =
        sources.firstOrNull { it.descriptor.id == ref.source }?.findByRecordId(ref)

    private fun mergedByIsbn(hits: List<BookCandidate>): List<BookCandidate> {
        // Hits without an ISBN key on themselves, so each stays a group of its own
        val groups = hits.groupBy { hit -> hit.isbn ?: Any() }
        return groups.values.map { group ->
            group.singleOrNull() ?: merge.merge(
                candidates = group,
                isbn = group.first().isbn
            )
        }
    }

    /**
     * Asks every provider at once, each on its own virtual thread, and waits for all of them.
     * Answers come back in [providers]' order.
     *
     * Once the request thread is interrupted (shutdown, or a cancelled call), it stops waiting: a
     * provider that has not answered yet counts as failed, so a search still returns the hits found
     * so far and otherwise the request is a 503 rather than a miss. The providers still running are
     * left to finish, not interrupted — an interrupted HTTP call would count against a healthy
     * source's circuit breaker (a deliberate deviation from the plan's `shutdownNow()`, 9.9 I16).
     */
    private fun <T> fanOut(providers: List<BookMetadataSource>, call: (BookMetadataSource) -> T): List<Answer<T>> {
        if (providers.isEmpty()) return emptyList()
        if (Thread.currentThread().isInterrupted) return providers.map(::notAsked)

        val executor = Executors.newVirtualThreadPerTaskExecutor()
        try {
            val futures = providers.map { provider -> executor.submit(Callable { ask { call(provider) } }) }
            return providers.zip(futures).map { (provider, future) -> await(provider, future) }
        } finally {
            executor.shutdown()
        }
    }

    private fun <T> await(provider: BookMetadataSource, future: Future<Answer<T>>): Answer<T> {
        if (Thread.currentThread().isInterrupted) {
            return if (future.state() == Future.State.SUCCESS) future.resultNow() else notAsked(provider)
        }
        return try {
            future.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            notAsked(provider)
        } catch (e: ExecutionException) {
            // Not a source failure — those are answers — but a bug, which stays a 500 as before
            throw e.cause ?: e
        }
    }

    private fun <T> ask(block: () -> T): Answer<T> = try {
        Answer.Answered(block())
    } catch (e: SourceQuotaExhaustedException) {
        Answer.Skipped(e)
    } catch (e: SourceUnavailableException) {
        Answer.Failed(e)
    }

    private fun <T> notAsked(provider: BookMetadataSource): Answer<T> =
        Answer.Failed(
            SourceUnavailableException(
                provider.descriptor.id,
                InterruptedException("Not asked: the request was interrupted")
            )
        )

    /** What one provider did: answered cleanly (a hit or a miss), failed, or was skipped for its quota. */
    private sealed interface Answer<out T> {
        data class Answered<T>(val value: T) : Answer<T>
        data class Failed(val error: SourceUnavailableException) : Answer<Nothing>
        data class Skipped(val error: SourceQuotaExhaustedException) : Answer<Nothing>

        fun valueOrNull(): T? = (this as? Answered<T>)?.value
    }

    /** Collected on the request thread from the answers, so no state is shared between providers. */
    private class Outcome(answers: List<Answer<*>>) {
        private val answered = answers.count { it is Answer.Answered }
        private val failures = answers.filterIsInstance<Answer.Failed>().map { it.error }
        private val skipped = answers.filterIsInstance<Answer.Skipped>().map { it.error }

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

        /** For an interrupted request: the providers that did not answer, or the request itself if all did. */
        fun interrupted(): SourceUnavailableException =
            if (failures.isNotEmpty()) combined(failures + skipped)
            else SourceUnavailableException(COMPOSITE_ID, InterruptedException("The request was interrupted"))

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
