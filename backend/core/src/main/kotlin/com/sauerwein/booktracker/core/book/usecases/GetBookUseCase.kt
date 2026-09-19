package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.shared.exception.NotFoundException

class GetBookUseCase(private val bookRepository: BookRepository) {
    operator fun invoke(id: BookId): Book {
        return bookRepository.findById(id) ?: throw NotFoundException("Book", id.value)
    }
}
