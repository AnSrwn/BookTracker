package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceRecordId

/**
 * A miss is an empty list or `null`. A source that cannot answer at all throws
 * [SourceUnavailableException], so an outage never masquerades as "not found".
 */
interface BookMetadataSource {
    val descriptor: SourceDescriptor
    fun search(text: String): List<BookCandidate>
    fun findByIsbn(isbn: Isbn): BookCandidate?

    /** The full record behind one of this source's own record ids, e.g. a search hit the user chose to add. */
    fun findByRecordId(recordId: SourceRecordId): BookCandidate?
}
