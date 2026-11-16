package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceDescriptor

/**
 * Records what it was asked for, so a test can assert which port method a use case chose —
 * the routing decision is the behaviour worth checking, not the returned data.
 */
class FakeBookMetadataSource(
    override val descriptor: SourceDescriptor = testDescriptor(),
    private val searchResults: List<BookCandidate> = emptyList(),
    private val isbnResult: BookCandidate? = null,
) : BookMetadataSource {

    val searchedTexts = mutableListOf<String>()
    val lookedUpIsbns = mutableListOf<Isbn>()

    override fun search(text: String): List<BookCandidate> {
        searchedTexts += text
        return searchResults
    }

    override fun findByIsbn(isbn: Isbn): BookCandidate? {
        lookedUpIsbns += isbn
        return isbnResult
    }
}
