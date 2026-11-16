package com.sauerwein.booktracker.adapter.web.catalog.dtos

/**
 * No provenance: which source a hit came from never leaves the backend (D28). The [id] is all the app needs.
 *
 * Some hits are shown only to help find a book, and their data may not be stored anywhere — not
 * in the catalog and not in a private book (9.7 D6). Every such hit carries an [id]. So:
 * - a result **with** an [id] is added via `POST /books/catalog/{id}`, never copied into a form;
 *   if that add answers 404, open an **empty** manual form, carrying nothing over (9.7 D9);
 * - only a result **without** an [id] may be copied into a private book;
 * - "make my own version" of a catalog book starts from the stored book (`GET /books/{id}`),
 *   never from a search hit.
 */
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
