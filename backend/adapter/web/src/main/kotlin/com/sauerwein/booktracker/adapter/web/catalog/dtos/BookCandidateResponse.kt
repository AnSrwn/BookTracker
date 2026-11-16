package com.sauerwein.booktracker.adapter.web.catalog.dtos

data class BookCandidateResponse(
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val isbn: String?,
    val publicationYear: Int?,
    val pageCount: Int?,
    val coverUrl: String?,
    val source: String,
    val sourceId: String?,
)
