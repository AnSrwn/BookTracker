package com.sauerwein.booktracker.adapter.web.book.dtos

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.*

@Schema(description = "A book: a shared catalog book or one of your private books.")
data class BookResponse(
    @field:Schema(description = "The book's id. Use it with `GET /api/v1/books/{id}`.", example = "3f2b8c1e-5d4a-4b7e-9c1f-2a6d8e0b4c71")
    val id: UUID,
    /** `CATALOG` (shared by everyone, read-only) or `PRIVATE` (the caller's own). */
    @field:Schema(
        description = "`CATALOG`: shared by all users, nobody can change it. `PRIVATE`: your own book, only you see it.",
        allowableValues = ["CATALOG", "PRIVATE"],
        example = "CATALOG",
    )
    val kind: String,
    @field:Schema(example = "Nineteen Eighty-Four")
    val title: String,
    @field:Schema(description = "Author names. Can be empty.", example = "[\"George Orwell\"]")
    val authors: List<String>,
    @field:Schema(example = "Penguin")
    val publisher: String?,
    @field:Schema(description = "Always an ISBN-13 without dashes; an ISBN-10 is converted.", example = "9780141187761")
    val isbn: String?,
    @field:Schema(description = "Year this edition was published.")
    val publicationYear: Int?,
    val pageCount: Int?,
    @field:Schema(description = "Link to a cover image.")
    val coverUrl: String?,
    /** The edition's language as a BCP 47 language subtag (`de`, `en`), `null` where unknown. */
    @field:Schema(description = "The language of this edition as a language code (`de`, `en`). `null` if unknown.", example = "en")
    val language: String?,
    @field:Schema(description = "When the book was created in BookTracker.")
    val createdAt: Instant,
    @field:Schema(description = "When the book's data last changed.")
    val updatedAt: Instant,
)
