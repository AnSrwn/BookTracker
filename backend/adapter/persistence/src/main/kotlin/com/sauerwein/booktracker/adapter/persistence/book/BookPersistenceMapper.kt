package com.sauerwein.booktracker.adapter.persistence.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.user.UserId

fun BookEntity.toDomain() = Book(
    id = BookId(id),
    ownerId = ownerId?.let(::UserId),
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
    ownerId = ownerId?.value,
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