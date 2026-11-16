package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.shared.exception.ConflictException
import com.sauerwein.booktracker.core.user.UserId

/** Mirrors the rules the SQL and the unique indexes apply; a fake that skipped them would make the use case tests meaningless. */
class InMemoryBookRepository : BookRepository {
    private val books = mutableMapOf<BookId, Book>()

    override fun add(book: Book) {
        requireUniqueIsbn(book)
        books[book.id] = book
    }

    override fun update(book: Book) {
        requireUniqueIsbn(book)
        books[book.id] = book
    }

    override fun findVisible(id: BookId, user: UserId): Book? = books[id]?.takeIf { it.isVisibleTo(user) }

    override fun findAllVisible(user: UserId): List<Book> = books.values.filter { it.isVisibleTo(user) }

    override fun findCatalogByIsbn(isbn: Isbn): Book? = books.values.find { it.ownerId == null && it.isbn == isbn }

    override fun searchVisible(text: String, user: UserId, limit: Int): List<Book> {
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return books.values
            .filter { it.isVisibleTo(user) }
            .filter { book ->
                val haystack = (listOf(book.title) + book.authors.map { it.name }).joinToString(" ").lowercase()
                words.all { it in haystack }
            }
            .sortedBy { it.title }
            .take(limit)
    }

    override fun upsertCatalogEntry(book: Book): Book {
        val existing = books.values.find {
            it.ownerId == null && it.source == book.source && it.sourceId == book.sourceId
        }
        val stored = existing?.let { book.copy(id = it.id, createdAt = it.createdAt) } ?: book
        requireUniqueIsbn(stored)
        books[stored.id] = stored
        return stored
    }

    private fun Book.isVisibleTo(user: UserId) = ownerId == null || ownerId == user

    private fun requireUniqueIsbn(book: Book) {
        if (book.isbn == null) return
        val clash = books.values.any { it.id != book.id && it.ownerId == book.ownerId && it.isbn == book.isbn }
        if (clash) throw ConflictException(code = "DuplicateBook", message = "Book with ISBN ${book.isbn.value} already exists")
    }

    /** Test-only view of everything stored, to assert that nothing was written. */
    fun stored(): List<Book> = books.values.toList()
}
