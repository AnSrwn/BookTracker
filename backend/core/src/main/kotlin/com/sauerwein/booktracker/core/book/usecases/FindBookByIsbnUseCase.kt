package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.shared.exception.NotFoundException

class FindBookByIsbnUseCase(private val bookMetadataSource: BookMetadataSource) {
    operator fun invoke(isbn: Isbn): BookCandidate {
        return bookMetadataSource.findByIsbn(isbn) ?: throw NotFoundException("Book", isbn.value)
    }
}
