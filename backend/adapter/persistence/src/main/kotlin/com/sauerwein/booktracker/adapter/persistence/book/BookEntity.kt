package com.sauerwein.booktracker.adapter.persistence.book

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("book")
data class BookEntity(
    @Id val id: UUID,
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