package com.sauerwein.booktracker.adapter.web.common.exception

data class ValidationError(
    val field: String,
    val code: String?,
    val message: String?,
)
