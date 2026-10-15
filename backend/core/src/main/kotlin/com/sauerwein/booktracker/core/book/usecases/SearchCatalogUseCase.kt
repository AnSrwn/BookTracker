package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.SearchQuery

class SearchCatalogUseCase(private val bookMetadataSource: BookMetadataSource) {
    operator fun invoke(rawQuery: String): List<BookCandidate> {
        return when (val query = SearchQuery.of(rawQuery)) {
            is SearchQuery.ByIsbn -> listOfNotNull(bookMetadataSource.findByIsbn(query.isbn))
            is SearchQuery.ByText -> bookMetadataSource.search(query.text)
        }
    }
}
