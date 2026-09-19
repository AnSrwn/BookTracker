package com.sauerwein.booktracker.adapter.web.book.dtos

import java.time.Instant
import java.util.*

data class BookResponse(
    val id: UUID,
    val title: String,
    val author: String,
    val publisher: String?,
    val isbn: String?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
