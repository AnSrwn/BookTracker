package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.user.UserId
import java.time.Instant
import java.util.*

fun testBook(
    ownerId: UserId?,
    title: String = "Dune",
    id: BookId = BookId(UUID.randomUUID()),
    author: String = "Frank Herbert",
    isbn: Isbn? = null,
    source: SourceId = SourceId.MANUAL,
    sourceId: SourceRecordId? = null,
    language: Language? = null,
) = Book(
    id = id,
    ownerId = ownerId,
    title = title,
    authors = listOf(Author(author, ref = null)),
    publisher = null,
    isbn = isbn,
    publicationYear = null,
    pageCount = null,
    coverUrl = null,
    language = language,
    source = source,
    sourceId = sourceId,
    sourceFetchedAt = null,
    workKey = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
