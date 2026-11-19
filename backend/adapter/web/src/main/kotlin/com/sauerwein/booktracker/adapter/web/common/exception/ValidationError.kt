package com.sauerwein.booktracker.adapter.web.common.exception

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "One field that failed validation.")
data class ValidationError(
    @field:Schema(description = "The field that failed.", example = "title")
    val field: String,
    @field:Schema(description = "A fixed, machine-readable reason.", example = "NotBlank")
    val code: String?,
    @field:Schema(description = "What is wrong, readable by people.", example = "must not be blank")
    val message: String?,
)
