package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn

/**
 * A miss is an empty list or `null`. A source that cannot answer at all throws
 * [SourceUnavailableException], so an outage never masquerades as "not found".
 */
interface BookMetadataSource {
    val descriptor: SourceDescriptor
    fun search(text: String): List<BookCandidate>
    fun findByIsbn(isbn: Isbn): BookCandidate?
}
