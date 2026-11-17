package com.sauerwein.booktracker.core.book.models

/**
 * A book as a lookup or search returns it. [bookId] and [kind] are set once it is stored —
 * a catalog row, or one of the caller's own private books — and `null` for a source hit that
 * may not or could not enter the catalog; the app can only create a private book from those.
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
    val bookId: BookId? = null,
    val kind: BookKind? = null,
)
