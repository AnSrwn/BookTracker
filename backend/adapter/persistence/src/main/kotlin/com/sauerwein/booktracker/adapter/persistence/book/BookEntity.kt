package com.sauerwein.booktracker.adapter.persistence.book

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

/** `review_state` is deliberately unmapped: inserts take the column default, updates never touch it. */
@Table("book")
data class BookEntity(
    @Id val id: UUID,
    val ownerId: UUID?,
    val title: String,
    @MappedCollection(idColumn = "book_id", keyColumn = "position")
    val authors: List<BookAuthorEntity>,
    val publisher: String?,
    val isbn: String?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
    val language: String?,
    val source: String,
    val sourceId: String?,
    val sourceFetchedAt: Instant?,
    val workKey: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Table("book_author")
data class BookAuthorEntity(
    val name: String,
    val source: String?,
    val sourceAuthorId: String?,
)
