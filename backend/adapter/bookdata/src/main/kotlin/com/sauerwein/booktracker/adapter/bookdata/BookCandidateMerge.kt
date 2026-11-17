package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId

/**
 * Merges what several providers know about **one edition** — one ISBN — field by field: first
 * non-null wins over the configured order ([MergePrecedence]). A provider that has nothing for
 * a field contributes nothing, so no rule ever asks which provider answered (ADR-007).
 *
 * - The language is decided first; its order then picks the order of every other field.
 * - Authors are one field: the first non-empty list wins whole, with its refs. Two lists are
 *   never combined — the same person spelled twice would appear twice.
 * - The candidate is named after the record whose title it carries: that record's `source` and
 *   `sourceId` become the identity of a stored row (9.9 I8).
 * - **A merged candidate is only as storable as its least storable contribution** (9.9 I12):
 *   persistable only if every candidate that supplied a field is. So data that may not be stored
 *   (Google, ADR-007) can never ride into a catalog row under another provider's identity.
 *
 * Never merges different ISBNs: grouping by edition is the caller's job, and editions of one work
 * stay apart (ADR-008).
 */
class BookCandidateMerge(private val precedence: MergePrecedence) {

    /**
     * [candidates] all describe [isbn] (or, without one, the same record); their order breaks ties
     * between candidates of one provider. [isbn] is the merged candidate's ISBN — the one asked for
     * or shared — rather than whichever form a provider reports.
     */
    fun merge(candidates: List<BookCandidate>, isbn: Isbn?): BookCandidate {
        require(candidates.isNotEmpty()) { "Nothing to merge" }
        val contributors = mutableSetOf<BookCandidate>()

        fun <T : Any> pick(order: List<SourceId>, value: (BookCandidate) -> T?): T? =
            ranked(candidates, order).firstNotNullOfOrNull { candidate -> value(candidate)?.also { contributors += candidate } }

        val language = pick(precedence.forLanguage()) { it.language }
        fun <T : Any> field(field: MergeField, value: (BookCandidate) -> T?): T? =
            pick(precedence.forField(field, language), value)

        // Every candidate has a title and an author: a provider drops records without them
        val named = ranked(candidates, precedence.forField(MergeField.TITLE, language)).first()
        contributors += named

        return BookCandidate(
            title = named.title,
            authors = field(MergeField.AUTHORS) { it.authors.ifEmpty { null } } ?: named.authors,
            publisher = field(MergeField.PUBLISHER) { it.publisher },
            isbn = isbn ?: named.isbn,
            publicationYear = field(MergeField.PUBLICATION_YEAR) { it.publicationYear },
            pageCount = field(MergeField.PAGE_COUNT) { it.pageCount },
            coverUrl = field(MergeField.COVER) { it.coverUrl },
            language = language,
            source = named.source,
            sourceId = named.sourceId,
            workKey = field(MergeField.WORK_KEY) { it.workKey },
            persistableInPublicCatalog = contributors.all { it.persistableInPublicCatalog },
        )
    }

    companion object {
        /** Listed providers first, in their order; the rest after, by id; ties keep their position. */
        fun ranked(candidates: List<BookCandidate>, order: List<SourceId>): List<BookCandidate> {
            val rank = order.withIndex().associate { (index, id) -> id to index }
            return candidates.sortedWith(
                compareBy<BookCandidate> { rank[it.source] ?: Int.MAX_VALUE }.thenBy { it.source.value },
            )
        }
    }
}
