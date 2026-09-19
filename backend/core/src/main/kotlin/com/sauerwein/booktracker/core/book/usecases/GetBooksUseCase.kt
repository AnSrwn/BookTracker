package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book

class GetBooksUseCase(private val bookRepository: BookRepository) {
    operator fun invoke(): List<Book> {
        return bookRepository.findAll()
    }
}
