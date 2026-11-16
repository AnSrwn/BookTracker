package com.sauerwein.booktracker.adapter.web.catalog

import com.sauerwein.booktracker.adapter.web.catalog.dtos.BookCandidateResponse
import com.sauerwein.booktracker.core.book.CatalogHitIds
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.BookKind
import com.sauerwein.booktracker.core.book.models.isAddable

/**
 * A stored book keeps its UUID. A hit that may be added but is not stored yet gets a signed
 * hit id (D28), so the app sends one kind of id for both and never learns the source.
 */
fun BookCandidate.toResponse(hitIds: CatalogHitIds): BookCandidateResponse {
    val hitId = if (bookId == null && isAddable) sourceId?.let { hitIds.encode(source, it) } else null

    return BookCandidateResponse(
        id = bookId?.value?.toString() ?: hitId,
        kind = kind?.name ?: hitId?.let { BookKind.CATALOG.name },
        title = title,
        authors = authors.map { it.name },
        publisher = publisher,
        isbn = isbn?.value,
        publicationYear = publicationYear,
        pageCount = pageCount,
        coverUrl = coverUrl,
    )
}
