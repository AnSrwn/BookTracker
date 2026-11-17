package com.sauerwein.booktracker.core.book.models

import com.sauerwein.booktracker.core.shared.exception.ValidationException
import com.sauerwein.booktracker.core.user.UserId
import java.net.URI
import java.time.Instant
import java.util.*

@JvmInline
value class BookId(val value: UUID)

data class Book(
    val id: BookId,
    val ownerId: UserId?,
    val title: String,
    /** May be empty: anonymous, sacred, reference and edited works have no author (the Duden, the Bible). */
    val authors: List<Author>,
    val publisher: String?,
    val isbn: Isbn?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
    val language: Language?,
    val source: SourceId,
    val sourceId: SourceRecordId?,
    val sourceFetchedAt: Instant?,
    /** Open Library's work key where known (ADR-008) — an option for grouping later, read by nothing yet. */
    val workKey: SourceRecordId?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(title.isNotBlank()) { "Title must not be blank" }
        require(authors.all { it.name.isNotBlank() }) { "Author names must not be blank" }
        require(pageCount == null || pageCount > 0) { "Page count must be positive" }

        if (coverUrl != null && !isHttpUrl(coverUrl)) {
            throw ValidationException(
                field = "coverUrl",
                code = "InvalidCoverUrl",
                message = "Cover URL must be an absolute http(s) URL: '$coverUrl'",
            )
        }
    }
}

private val ALLOWED_URL_SCHEMES = setOf("http", "https")

private fun isHttpUrl(value: String): Boolean {
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in ALLOWED_URL_SCHEMES && !uri.host.isNullOrBlank()
}
