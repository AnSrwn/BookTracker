package com.sauerwein.booktracker.core.book

interface BookRepository {
    fun add(book: Book)
    fun update(book: Book)
    fun findById(id: BookId): Book?
    fun findAll(): List<Book>
}