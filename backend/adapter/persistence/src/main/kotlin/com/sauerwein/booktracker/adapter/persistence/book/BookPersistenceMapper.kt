package com.sauerwein.booktracker.adapter.persistence.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn

fun BookEntity.toDomain() = Book(
    id = BookId(id),
    title = title,
    author = author,
    publisher = publisher,
    isbn = isbn?.let(Isbn::of),
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Book.toEntity() = BookEntity(
    id = id.value,
    title = title,
    author = author,
    publisher = publisher,
    isbn = isbn?.value,
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)