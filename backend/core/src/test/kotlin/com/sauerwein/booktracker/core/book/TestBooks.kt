package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.user.UserId
import java.time.Instant
import java.util.*

fun testBook(
    ownerId: UserId?,
    title: String = "Dune",
    id: BookId = BookId(UUID.randomUUID()),
) = Book(
    id = id,
    ownerId = ownerId,
    title = title,
    author = "Frank Herbert",
    publisher = null,
    isbn = null,
    publicationYear = null,
    pageCount = null,
    coverUrl = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
