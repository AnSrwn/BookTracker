package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.user.UserId

class InMemoryBookRepository : BookRepository {
    private val books = mutableMapOf<BookId, Book>()

    override fun add(book: Book) {
        books[book.id] = book
    }

    override fun update(book: Book) {
        books[book.id] = book
    }

    override fun findVisible(id: BookId, user: UserId): Book? = books[id]?.takeIf { it.isVisibleTo(user) }

    override fun findAllVisible(user: UserId): List<Book> = books.values.filter { it.isVisibleTo(user) }

    /** Mirrors the rule the SQL applies; a fake that skips it would make the use case tests meaningless. */
    private fun Book.isVisibleTo(user: UserId) = ownerId == null || ownerId == user

    /** Test-only view of everything stored, to assert that nothing was written. */
    fun stored(): List<Book> = books.values.toList()
}
