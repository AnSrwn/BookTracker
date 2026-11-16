package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef

/**
 * Records what it was asked for, so a test can assert which port method a use case chose —
 * the routing decision is the behaviour worth checking, not the returned data. Like every
 * source it answers `null` for a record of another source.
 */
class FakeBookMetadataSource(
    override val descriptor: SourceDescriptor = testDescriptor(),
    private val searchResults: List<BookCandidate> = emptyList(),
    private val isbnResult: BookCandidate? = null,
    private val recordResult: BookCandidate? = null,
    private val searchFailure: RuntimeException? = null,
    private val isbnFailure: RuntimeException? = null,
    private val recordFailure: RuntimeException? = null,
) : BookMetadataSource {

    val searchedTexts = mutableListOf<String>()
    val lookedUpIsbns = mutableListOf<Isbn>()
    val lookedUpRecordIds = mutableListOf<SourceRecordId>()

    override fun search(text: String): List<BookCandidate> {
        searchedTexts += text
        searchFailure?.let { throw it }
        return searchResults
    }

    override fun findByIsbn(isbn: Isbn): BookCandidate? {
        lookedUpIsbns += isbn
        isbnFailure?.let { throw it }
        return isbnResult
    }

    override fun findByRecordId(ref: SourceRef): BookCandidate? {
        if (ref.source != descriptor.id) return null
        lookedUpRecordIds += ref.recordId
        recordFailure?.let { throw it }
        return recordResult
    }
}
