package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceRecordId

/**
 * Records what it was asked for, so a test can assert which port method a use case chose —
 * the routing decision is the behaviour worth checking, not the returned data.
 */
class FakeBookMetadataSource(
    override val descriptor: SourceDescriptor = testDescriptor(),
    private val searchResults: List<BookCandidate> = emptyList(),
    private val isbnResult: BookCandidate? = null,
    private val recordResult: BookCandidate? = null,
    private val recordFailure: RuntimeException? = null,
) : BookMetadataSource {

    val searchedTexts = mutableListOf<String>()
    val lookedUpIsbns = mutableListOf<Isbn>()
    val lookedUpRecordIds = mutableListOf<SourceRecordId>()

    override fun search(text: String): List<BookCandidate> {
        searchedTexts += text
        return searchResults
    }

    override fun findByIsbn(isbn: Isbn): BookCandidate? {
        lookedUpIsbns += isbn
        return isbnResult
    }

    override fun findByRecordId(recordId: SourceRecordId): BookCandidate? {
        lookedUpRecordIds += recordId
        recordFailure?.let { throw it }
        return recordResult
    }
}
