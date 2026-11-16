package com.sauerwein.booktracker.adapter.web.book

import com.sauerwein.booktracker.adapter.web.book.dtos.BookResponse
import com.sauerwein.booktracker.adapter.web.book.dtos.CreateBookRequest
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.usecases.AddBookCommand

fun CreateBookRequest.toCommand() = AddBookCommand(
    title = title,
    authors = authors.map { it.trim() },
    publisher = publisher,
    isbn = isbn?.let(Isbn::of),
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
)

fun Book.toResponse() = BookResponse(
    id = id.value,
    title = title,
    authors = authors.map { it.name },
    publisher = publisher,
    isbn = isbn?.value,
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
