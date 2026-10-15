package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn

interface BookMetadataSource {
    val descriptor: SourceDescriptor
    fun search(text: String): List<BookCandidate>
    fun findByIsbn(isbn: Isbn): BookCandidate?
}
