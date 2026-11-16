package com.sauerwein.booktracker.adapter.web.catalog.dtos

/** No provenance: which source a hit came from never leaves the backend (D28). The [id] is all the app needs. */
data class BookCandidateResponse(
    /**
     * Opaque. A stored book's UUID, or a signed id for a search hit not stored yet. With [kind]
     * `CATALOG`, hand it to `POST /books/catalog/{id}` as is. `null`: the hit cannot be added,
     * only copied into a private book.
     */
    val id: String?,
    /** `CATALOG` (can be added), `PRIVATE` (one of the caller's own books), or `null`. */
    val kind: String?,
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn: String?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
)
