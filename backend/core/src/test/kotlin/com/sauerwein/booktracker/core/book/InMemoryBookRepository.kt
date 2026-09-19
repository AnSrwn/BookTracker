package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId

class InMemoryBookRepository : BookRepository {
    private val books = mutableMapOf<BookId, Book>()

    override fun add(book: Book) {
        books[book.id] = book
    }

    override fun update(book: Book) {
        books[book.id] = book
    }

    override fun findById(id: BookId): Book? = books[id]

    override fun findAll(): List<Book> = books.values.toList()
}
