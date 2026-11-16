package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.BookKind
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SearchQuery
import com.sauerwein.booktracker.core.book.models.isDeadEnd
import com.sauerwein.booktracker.core.book.models.toCandidate
import com.sauerwein.booktracker.core.user.CurrentUserProvider

private const val LOCAL_SEARCH_LIMIT = 20

/**
 * Local hits first. With at least [minLocalHits] of them the source is not asked at all;
 * with fewer, the source's hits are appended. Search only reads (D27): a source hit is stored
 * when a user adds it, from the full record — never as the thin record a search returns.
 *
 * Outages (9.7 D2, D4): with local hits a failing source only shortens the list; without any,
 * the failure is the answer (503), so an outage never looks like "no such book".
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

        val external = try {
            bookMetadataSource.search(text)
        } catch (e: SourceUnavailableException) {
            if (local.isEmpty()) throw e
            emptyList()
        }

        val localKeys = local.mapNotNull { candidate -> candidate.sourceId?.let { candidate.source to it } }.toSet()
        return withoutIsbnDuplicates(
            local + external
                .filterNot { candidate -> candidate.sourceId?.let { candidate.source to it } in localKeys }
                .filterNot { it.isDeadEnd },
        )
    }

    /**
     * 9.7 D10: a hit is dropped when an earlier catalog result already has its ISBN — first one
     * wins as it is, merging fields is 9.9. The caller's private books suppress nothing (A4): a
     * catalog copy and a private copy of one ISBN may sit side by side (9.6 D12).
     */
    private fun withoutIsbnDuplicates(results: List<BookCandidate>): List<BookCandidate> {
        val seen = mutableSetOf<Isbn>()
        return results.filter { candidate ->
            val isbn = candidate.isbn
            when {
                candidate.kind == BookKind.PRIVATE || isbn == null -> true
                else -> seen.add(isbn)
            }
        }
    }
}
