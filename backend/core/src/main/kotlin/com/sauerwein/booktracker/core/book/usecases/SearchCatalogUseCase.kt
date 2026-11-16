package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.SearchQuery
import com.sauerwein.booktracker.core.book.models.toCandidate
import com.sauerwein.booktracker.core.user.CurrentUserProvider

private const val LOCAL_SEARCH_LIMIT = 20

/**
 * Local hits first. With at least [minLocalHits] of them the source is not asked at all;
 * with fewer, the source's hits are appended. Search only reads (D27): a source hit is stored
 * when a user adds it, from the full record — never as the thin record a search returns.
 */
class SearchCatalogUseCase(
    private val resolveIsbnUseCase: ResolveIsbnUseCase,
    private val bookRepository: BookRepository,
    private val bookMetadataSource: BookMetadataSource,
    private val currentUserProvider: CurrentUserProvider,
    private val minLocalHits: Int,
) {
    operator fun invoke(rawQuery: String): List<BookCandidate> {
        return when (val query = SearchQuery.of(rawQuery)) {
            is SearchQuery.ByIsbn -> listOfNotNull(resolveIsbnUseCase(query.isbn))
            is SearchQuery.ByText -> searchText(query.text)
        }
    }

    private fun searchText(text: String): List<BookCandidate> {
        val user = currentUserProvider.currentUserId()
        val local = bookRepository.searchVisible(text = text, user = user, limit = LOCAL_SEARCH_LIMIT)
            .map { it.toCandidate() }
        if (local.size >= minLocalHits) return local

        val localKeys = local.mapNotNull { candidate -> candidate.sourceId?.let { candidate.source to it } }.toSet()
        val external = bookMetadataSource.search(text)
            .filterNot { candidate -> candidate.sourceId?.let { candidate.source to it } in localKeys }
        return local + external
    }
}
