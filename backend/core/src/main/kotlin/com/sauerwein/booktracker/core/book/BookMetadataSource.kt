package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceRef

/**
 * A miss is an empty list or `null`. A source that cannot answer at all throws
 * [SourceUnavailableException], so an outage never masquerades as "not found". A candidate may
 * have no author: a record without one is still a book (9.10 Q9).
 */
interface BookMetadataSource {
    val descriptor: SourceDescriptor
    fun search(text: String): List<BookCandidate>
    fun findByIsbn(isbn: Isbn): BookCandidate?

    /**
     * The full record behind a source's own record id, e.g. a search hit the user chose to add.
     * `null` for a record of another source: the ref names its source, so a composite can route it.
     */
    fun findByRecordId(ref: SourceRef): BookCandidate?
}
