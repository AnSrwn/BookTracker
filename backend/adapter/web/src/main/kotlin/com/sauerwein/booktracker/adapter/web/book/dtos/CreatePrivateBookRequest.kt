package com.sauerwein.booktracker.adapter.web.book.dtos

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class CreatePrivateBookRequest(
    @field:NotBlank val title: String,
    /** Optional: a book may have no author — omitted, `null` and `[]` all mean none. Names that are sent must not be blank. */
    val authors: List<@NotBlank String>? = null,
    val publisher: String? = null,
    val isbn: String? = null,
    val publicationYear: Int? = null,
    @field:Positive val pageCount: Int? = null,
    val coverUrl: String? = null,
    /**
     * Optional BCP 47 language tag. Normalised to its language subtag (`de-AT` → `de`, `ger` → `de`);
     * anything that is not a language is a 400.
     */
    val language: String? = null,
)
