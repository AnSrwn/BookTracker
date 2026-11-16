package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.shared.exception.NotFoundException

class FindBookByIsbnUseCase(private val resolveIsbnUseCase: ResolveIsbnUseCase) {
    operator fun invoke(isbn: Isbn): BookCandidate {
        return resolveIsbnUseCase(isbn) ?: throw NotFoundException("Book", isbn.value)
    }
}
