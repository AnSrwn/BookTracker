package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.user.UserId

interface BookRepository {
    fun add(book: Book)
    fun update(book: Book)
    fun findVisible(id: BookId, user: UserId): Book?
    fun findAllVisible(user: UserId): List<Book>
}
