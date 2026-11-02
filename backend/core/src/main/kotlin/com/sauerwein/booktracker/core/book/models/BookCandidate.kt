package com.sauerwein.booktracker.core.book.models

data class BookCandidate (
    val title: String,
    val authors: List<Author>,
    val publisher: String?,
    val isbn: Isbn?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
    val source: SourceId,
    val sourceId: SourceRecordId?,
    val persistableInPublicCatalog: Boolean,
)
