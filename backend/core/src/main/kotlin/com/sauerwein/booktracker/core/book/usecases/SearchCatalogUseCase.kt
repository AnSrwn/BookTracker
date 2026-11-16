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
 * with fewer, the source's hits are appended. A source hit with an ISBN enters the catalog,
 * so every hit the app may add carries an id; a hit without one stays a suggestion the app
 * can only turn into a private book. Title and author are guaranteed by the mapper and `Book`.
 */
class SearchCatalogUseCase(
    private val resolveIsbnUseCase: ResolveIsbnUseCase,
    private val upsertCatalogEntryUseCase: UpsertCatalogEntryUseCase,
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
            .map(::storeIfComplete)
        return (local + external).distinctBy { it.bookId ?: it }
    }

    /** A search hit without an ISBN is too thin for the catalog: it could never be matched to a scan. */
    private fun storeIfComplete(candidate: BookCandidate): BookCandidate {
        if (candidate.isbn == null) return candidate
        return upsertCatalogEntryUseCase(candidate)?.toCandidate() ?: candidate
    }
}
