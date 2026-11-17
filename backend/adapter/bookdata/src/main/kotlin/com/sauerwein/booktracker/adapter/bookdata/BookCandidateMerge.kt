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
 *   never combined — the same person spelled twice would appear twice. A list a provider took
 *   from a broader record than the edition ([BookCandidate.authorsFromWork]) ranks below every
 *   other provider's record, even one without authors: Open Library's work may credit the
 *   translators of an anonymous epic, where DNB rightly names nobody (9.10 R16). With no list at
 *   all, the book has no author — some books have none (Q9).
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

        fun <T : Any> pick(order: List<SourceId>, from: List<BookCandidate>, value: (BookCandidate) -> T?): T? =
            ranked(from, order).firstNotNullOfOrNull { candidate -> value(candidate)?.also { contributors += candidate } }

        val language = pick(precedence.forLanguage(), candidates) { it.language }
        fun <T : Any> field(field: MergeField, from: List<BookCandidate> = candidates, value: (BookCandidate) -> T?): T? =
            pick(precedence.forField(field, language), from, value)

        // Every candidate has a title: a provider drops records without one
        val named = ranked(candidates, precedence.forField(MergeField.TITLE, language)).first()
        contributors += named

        // Work authors only where no provider has a record of the edition's own (R16)
        val direct = candidates.filterNot { it.authorsFromWork }
        val authorSources = direct.ifEmpty { candidates }
        val authors = field(MergeField.AUTHORS, from = authorSources) { candidate -> candidate.takeIf { it.authors.isNotEmpty() } }

        return BookCandidate(
            title = named.title,
            authors = authors?.authors.orEmpty(),
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
            authorsFromWork = authors?.authorsFromWork ?: false,
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
