package com.sauerwein.booktracker.adapter.web.book.dtos

data class CreateBookRequest(
    val title: String,
    val author: String,
    val publisher: String? = null,
    val isbn: String? = null,
    val publicationYear: Int? = null,
    val pageCount: Int? = null,
    val coverUrl: String? = null,
)
