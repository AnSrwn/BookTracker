package com.sauerwein.booktracker.core.book.models

/**
 * A book as a lookup or search returns it. [bookId] and [kind] are set once it is stored —
 * a catalog row, or one of the caller's own private books — and `null` for a source hit that
 * may not or could not enter the catalog; the app can only create a private book from those.
 * [authors] may be empty: some books have none (the Duden, the Bible).
 */
data class BookCandidate(
    val title: String,
    val authors: List<Author>,
    val publisher: String?,
    val isbn: Isbn?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
    val language: Language?,
    val source: SourceId,
    val sourceId: SourceRecordId?,
    val workKey: SourceRecordId?,
    val persistableInPublicCatalog: Boolean,
    /**
     * The authors were taken from a broader record than this edition — Open Library's work, when
     * the edition names none. Such a list can credit translators of an anonymous work, so a merge
     * ranks it below every other provider's record (9.10 R16). Never stored, never exposed.
     */
    val authorsFromWork: Boolean = false,
    val bookId: BookId? = null,
    val kind: BookKind? = null,
)
