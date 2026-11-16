package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.user.CurrentUserProvider

/**
 * The caller's books: exactly what is linked to them. Not "everything visible" — that would
 * list the whole public catalog for every user.
 */
class GetBooksUseCase(
    private val bookRepository: BookRepository,
    private val currentUserProvider: CurrentUserProvider,
) {
    operator fun invoke(): List<Book> {
        return bookRepository.findLibrary(currentUserProvider.currentUserId())
    }
}
