package com.sauerwein.booktracker.core.book.models

sealed interface SearchQuery {
    data class ByIsbn(val isbn: Isbn) : SearchQuery
    data class ByText(val text: String) : SearchQuery

    companion object {
        fun of(raw: String): SearchQuery {
            return Isbn.ofOrNull(rawValue = raw)?.let(::ByIsbn) ?: ByText(raw.trim())
        }
    }
}
