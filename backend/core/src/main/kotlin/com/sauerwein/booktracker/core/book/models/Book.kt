package com.sauerwein.booktracker.core.book.models

import java.time.Instant
import java.util.*

@JvmInline
value class BookId(val value: UUID)

data class Book(
    val id: BookId,
    val title: String,
    val author: String,
    val publisher: String?,
    val isbn: Isbn?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(title.isNotBlank()) { "Title must not be blank" }
        require(author.isNotBlank()) { "Author must not be blank" }
        require(pageCount == null || pageCount > 0) { "Page count must be positive" }
    }
}