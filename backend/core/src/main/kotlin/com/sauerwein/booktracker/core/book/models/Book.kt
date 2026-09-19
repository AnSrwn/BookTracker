package com.sauerwein.booktracker.core.book.models

import java.net.URI
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
        require(coverUrl == null || isHttpUrl(coverUrl)) { "Cover URL must be an absolute http(s) URL: '$coverUrl'" }
    }
}

private val ALLOWED_URL_SCHEMES = setOf("http", "https")

private fun isHttpUrl(value: String): Boolean {
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in ALLOWED_URL_SCHEMES && !uri.host.isNullOrBlank()
}
