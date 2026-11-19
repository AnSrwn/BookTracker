package com.sauerwein.booktracker.adapter.web.catalog.dtos

import io.swagger.v3.oas.annotations.media.Schema

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
@Schema(
    description = """A book found by search or ISBN lookup. What you may do with it depends on `kind` and `id`:

- `kind` = `CATALOG`: add it to your books with `POST /api/v1/books/catalog/{id}`. Don't copy its data into
  a private book: some sources only allow showing their data, not storing it.
- `kind` = `PRIVATE`: one of your own private books. It is already in your books.
- `id` and `kind` are `null`: the hit can't be added. You may use its data to create a private book.

To make your own version of a catalog book, load the stored book with `GET /api/v1/books/{id}` and start from that.""",
)
data class BookCandidateResponse(
    /**
     * Opaque. A stored book's UUID, or a signed id for a search hit not stored yet. With [kind]
     * `CATALOG`, hand it to `POST /books/catalog/{id}` as is. `null`: the hit cannot be added,
     * only copied into a private book.
     */
    @field:Schema(
        description = "Treat it as an opaque string and pass it on unchanged. A stored book's UUID, or a longer signed id " +
                "for a hit that is not stored yet. `null`: the hit can't be added.",
        example = "3f2b8c1e-5d4a-4b7e-9c1f-2a6d8e0b4c71",
    )
    val id: String?,
    /** `CATALOG` (can be added), `PRIVATE` (one of the caller's own books), or `null`. */
    @field:Schema(
        description = "`CATALOG`: can be added. `PRIVATE`: one of your own books. `null`: can't be added.",
        allowableValues = ["CATALOG", "PRIVATE"],
        example = "CATALOG",
    )
    val kind: String?,
    @field:Schema(example = "Nineteen Eighty-Four")
    val title: String,
    @field:Schema(description = "Author names. Can be empty.", example = "[\"George Orwell\"]")
    val authors: List<String>,
    @field:Schema(example = "Penguin")
    val publisher: String?,
    @field:Schema(description = "Always an ISBN-13 without dashes.", example = "9780141187761")
    val isbn: String?,
    @field:Schema(description = "Year this edition was published.")
    val publicationYear: Int?,
    val pageCount: Int?,
    @field:Schema(description = "Link to a cover image.")
    val coverUrl: String?,
    /** The edition's language as a BCP 47 language subtag (`de`, `en`), `null` where unknown. */
    @field:Schema(description = "The language of this edition as a language code (`de`, `en`). `null` if unknown.", example = "en")
    val language: String?,
)
