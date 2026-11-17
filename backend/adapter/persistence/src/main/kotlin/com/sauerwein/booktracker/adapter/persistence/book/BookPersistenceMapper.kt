package com.sauerwein.booktracker.adapter.persistence.book

import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import com.sauerwein.booktracker.core.user.UserId

fun BookEntity.toDomain() = Book(
    id = BookId(id),
    ownerId = ownerId?.let(::UserId),
    title = title,
    authors = authors.map { it.toDomain() },
    publisher = publisher,
    isbn = isbn?.let(Isbn::of),
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
    // Reading a stored row never fails over its language: one `Language` no longer accepts reads as unknown
    language = Language.ofOrNull(language),
    source = SourceId.of(source),
    sourceId = sourceId?.let(::SourceRecordId),
    sourceFetchedAt = sourceFetchedAt,
    workKey = workKey?.let(::SourceRecordId),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Book.toEntity() = BookEntity(
    id = id.value,
    ownerId = ownerId?.value,
    title = title,
    authors = authors.map { it.toEntity() },
    publisher = publisher,
    isbn = isbn?.value,
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
    language = language?.value,
    source = source.value,
    sourceId = sourceId?.value,
    sourceFetchedAt = sourceFetchedAt,
    workKey = workKey?.value,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BookAuthorEntity.toDomain() = Author(
    name = name,
    // The schema guarantees both or neither
    ref = if (source != null && sourceAuthorId != null) SourceRef(SourceId.of(source), SourceRecordId(sourceAuthorId)) else null,
)

private fun Author.toEntity() = BookAuthorEntity(
    name = name,
    source = ref?.source?.value,
    sourceAuthorId = ref?.recordId?.value,
)
