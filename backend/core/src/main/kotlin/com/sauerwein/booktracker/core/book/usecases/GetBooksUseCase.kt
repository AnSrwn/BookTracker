package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.user.CurrentUserProvider

class GetBooksUseCase(
    private val bookRepository: BookRepository,
    private val currentUserProvider: CurrentUserProvider,
) {
    operator fun invoke(): List<Book> {
        return bookRepository.findAllVisible(currentUserProvider.currentUserId())
    }
}
