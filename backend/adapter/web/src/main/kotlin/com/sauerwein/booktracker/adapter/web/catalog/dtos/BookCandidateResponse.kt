package com.sauerwein.booktracker.adapter.web.catalog.dtos

import java.util.*

data class BookCandidateResponse(
    /** Set when the book is stored: add it with `POST /books/catalog/{id}` if [kind] is `CATALOG`. */
    val id: UUID?,
    /** `CATALOG`, `PRIVATE` (one of the caller's own books), or `null` for a hit that is not stored. */
    val kind: String?,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn: String?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
    val source: String,
    val sourceId: String?,
)
