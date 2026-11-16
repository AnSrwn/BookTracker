package com.sauerwein.booktracker.adapter.web.catalog

import com.sauerwein.booktracker.adapter.web.catalog.dtos.BookCandidateResponse
import com.sauerwein.booktracker.core.book.models.BookCandidate

fun BookCandidate.toResponse() = BookCandidateResponse(
    id = bookId?.value,
    kind = kind?.name,
    title = title,
    authors = authors.map { it.name },
    publisher = publisher,
    isbn = isbn?.value,
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
    source = source.value,
    sourceId = sourceId?.value,
)
