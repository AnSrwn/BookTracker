package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import com.sauerwein.booktracker.core.user.CurrentUserProvider

class GetBookUseCase(
    private val bookRepository: BookRepository,
    private val currentUserProvider: CurrentUserProvider,
) {
    operator fun invoke(id: BookId): Book {
        val user = currentUserProvider.currentUserId()
        return bookRepository.findVisible(id, user) ?: throw NotFoundException("Book", id.value)
    }
}
