package com.sauerwein.booktracker.adapter.web.book

import com.sauerwein.booktracker.adapter.web.book.dtos.BookResponse
import com.sauerwein.booktracker.adapter.web.book.dtos.CreatePrivateBookRequest
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.kind
import com.sauerwein.booktracker.core.book.usecases.CreatePrivateBookCommand

fun CreatePrivateBookRequest.toCommand() = CreatePrivateBookCommand(
    title = title,
    authors = authors.orEmpty().map { it.trim() },
    publisher = publisher,
    isbn = isbn?.let(Isbn::of),
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
    language = language?.let(Language::of),
)

fun Book.toResponse() = BookResponse(
    id = id.value,
    kind = kind.name,
    title = title,
    authors = authors.map { it.name },
    publisher = publisher,
    isbn = isbn?.value,
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
    language = language?.value,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
