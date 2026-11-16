package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.shared.exception.ConflictException
import com.sauerwein.booktracker.core.user.UserId
import java.time.Instant

/** Mirrors the rules the SQL and the unique indexes apply; a fake that skipped them would make the use case tests meaningless. */
class InMemoryBookRepository : BookRepository {
    private val books = mutableMapOf<BookId, Book>()
    private val links = mutableListOf<Link>()

    private data class Link(val user: UserId, val bookId: BookId, val at: Instant)

    override fun addPrivateBook(book: Book) {
        val owner = requireNotNull(book.ownerId) { "A private book needs an owner" }
        books[book.id] = book
        links += Link(owner, book.id, book.createdAt)
    }

    override fun update(book: Book) {
        requireUniqueCatalogIsbn(book)
        books[book.id] = book
    }

    override fun findVisible(id: BookId, user: UserId): Book? = books[id]?.takeIf { it.isVisibleTo(user) }

    override fun findLibrary(user: UserId): List<Book> =
        links.filter { it.user == user }
            .sortedByDescending { it.at }
            .mapNotNull { books[it.bookId] }

    override fun addToLibrary(user: UserId, bookId: BookId, at: Instant) {
        require(bookId in books) { "Foreign key violation: book $bookId does not exist" }
        if (links.any { it.user == user && it.bookId == bookId }) {
            throw ConflictException(code = "AlreadyInLibrary", message = "Book is already in your books")
        }
        links += Link(user, bookId, at)
    }

    override fun findCatalogByIsbn(isbn: Isbn): Book? = books.values.find { it.ownerId == null && it.isbn == isbn }

    override fun findCatalogBySource(source: SourceId, sourceId: SourceRecordId): Book? =
        books.values.find { it.ownerId == null && it.source == source && it.sourceId == sourceId }

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
        requireUniqueCatalogIsbn(stored)
        books[stored.id] = stored
        return stored
    }

    private fun Book.isVisibleTo(user: UserId) = ownerId == null || ownerId == user

    /** `ux_book_catalog_isbn`: private books may repeat an ISBN, catalog rows may not. */
    private fun requireUniqueCatalogIsbn(book: Book) {
        if (book.isbn == null || book.ownerId != null) return
        val clash = books.values.any { it.id != book.id && it.ownerId == null && it.isbn == book.isbn }
        if (clash) throw ConflictException(
            code = "DuplicateBook",
            message = "Book with ISBN ${book.isbn.value} already exists"
        )
    }

    /** Test-only: stores a row as it is, without linking it to anyone — a catalog row, or another user's data. */
    fun seed(vararg seeded: Book) {
        seeded.forEach {
            requireUniqueCatalogIsbn(it)
            books[it.id] = it
        }
    }

    /** Test-only view of everything stored, to assert that nothing was written. */
    fun stored(): List<Book> = books.values.toList()

    /** Test-only view of the links, as `(user, book)` pairs. */
    fun links(): List<Pair<UserId, BookId>> = links.map { it.user to it.bookId }
}
