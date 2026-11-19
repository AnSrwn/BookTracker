package com.sauerwein.booktracker.adapter.web.book.dtos

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

@Schema(description = "The data of a new private book. Only `title` is required.")
data class CreatePrivateBookRequest(
    @field:Schema(description = "Must not be blank.", example = "Nineteen Eighty-Four")
    @field:NotBlank val title: String,
    /** Optional: a book may have no author — omitted, `null` and `[]` all mean none. Names that are sent must not be blank. */
    @field:Schema(
        description = "Author names. Optional: leaving it out, `null` and `[]` all mean no author. Names you send must not be blank.",
        example = "[\"George Orwell\"]",
    )
    val authors: List<@NotBlank String>? = null,
    @field:Schema(example = "Penguin")
    val publisher: String? = null,
    @field:Schema(
        description = "ISBN-10 or ISBN-13, dashes and spaces allowed. Stored as ISBN-13. An invalid ISBN is a 400.",
        example = "978-0-14-118776-1",
    )
    val isbn: String? = null,
    @field:Schema(description = "Year this edition was published.")
    val publicationYear: Int? = null,
    @field:Schema(description = "Must be greater than 0.")
    @field:Positive val pageCount: Int? = null,
    @field:Schema(description = "Link to a cover image.")
    val coverUrl: String? = null,
    /**
     * Optional BCP 47 language tag. Normalised to its language subtag (`de-AT` → `de`, `ger` → `de`);
     * anything that is not a language is a 400.
     */
    @field:Schema(
        description = "Language of the edition as a language tag. Only the language is kept: `de-AT` becomes `de`, " +
                "and `ger` becomes `de` too. Anything that is not a language is a 400.",
        example = "en",
    )
    val language: String? = null,
)
