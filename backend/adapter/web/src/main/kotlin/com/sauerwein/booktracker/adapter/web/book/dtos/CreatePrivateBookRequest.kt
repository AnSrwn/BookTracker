package com.sauerwein.booktracker.adapter.web.book.dtos

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

data class CreatePrivateBookRequest(
    @field:NotBlank val title: String,
    @field:NotEmpty val authors: List<@NotBlank String>,
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
