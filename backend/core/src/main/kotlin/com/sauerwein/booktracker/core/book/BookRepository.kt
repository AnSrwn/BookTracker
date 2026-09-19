package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId

interface BookRepository {
    fun add(book: Book)
    fun update(book: Book)
    fun findById(id: BookId): Book?
    fun findAll(): List<Book>
}