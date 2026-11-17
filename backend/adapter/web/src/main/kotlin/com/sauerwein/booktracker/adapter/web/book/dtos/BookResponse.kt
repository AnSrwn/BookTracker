package com.sauerwein.booktracker.adapter.web.book.dtos

import java.time.Instant
import java.util.*

data class BookResponse(
    val id: UUID,
    /** `CATALOG` (shared by everyone, read-only) or `PRIVATE` (the caller's own). */
    val kind: String,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn: String?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
    /** The edition's language as a BCP 47 language subtag (`de`, `en`), `null` where unknown. */
    val language: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
