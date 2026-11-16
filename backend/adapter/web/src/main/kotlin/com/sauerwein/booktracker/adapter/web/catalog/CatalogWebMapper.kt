package com.sauerwein.booktracker.adapter.web.catalog

import com.sauerwein.booktracker.adapter.web.catalog.dtos.BookCandidateResponse
import com.sauerwein.booktracker.core.book.CatalogHitIds
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.BookKind
import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.addRef

/**
 * A stored book keeps its UUID. A hit that may be added but is not stored yet gets a signed
 * hit id (D28) — for its source record, or for its ISBN alone if its data may not be stored
 * (9.7 D1) — so the app sends one kind of id for all of them and never learns the source.
 */
fun BookCandidate.toResponse(hitIds: CatalogHitIds): BookCandidateResponse {
    val id = when (val ref = addRef) {
        is CatalogBookRef.Stored -> ref.id.value.toString()
        is CatalogBookRef.SourceRecord -> hitIds.encode(ref)
        is CatalogBookRef.ByIsbn -> hitIds.encode(ref)
        null -> null
    }

    return BookCandidateResponse(
        id = id,
        kind = kind?.name ?: id?.let { BookKind.CATALOG.name },
        title = title,
        authors = authors.map { it.name },
        publisher = publisher,
        isbn = isbn?.value,
        publicationYear = publicationYear,
        pageCount = pageCount,
        coverUrl = coverUrl,
    )
}
