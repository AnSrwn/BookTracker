package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.user.UserId
import java.time.Instant

/**
 * Every read is either scoped to the caller or restricted to the approved public catalog
 * (ADR-006). A user's books are the ones linked to them — catalog rows are linked, never copied.
 */
interface BookRepository {
    /** Inserts a private book and links it to its owner, in one transaction. */
    fun addPrivateBook(book: Book)

    fun update(book: Book)

    fun findVisible(id: BookId, user: UserId): Book?

    /** The books linked to [user], newest link first. */
    fun findLibrary(user: UserId): List<Book>

    /** Links [bookId] to [user]. Throws `ConflictException` if it is already linked. */
    fun addToLibrary(user: UserId, bookId: BookId, at: Instant)

    /** Public, approved rows only — a user's private row is their edit, not a source's answer. */
    fun findCatalogByIsbn(isbn: Isbn): Book?

    /** Every word of [text] must appear in the title or an author name. */
    fun searchVisible(text: String, user: UserId, limit: Int): List<Book>

    /**
     * Inserts a public catalog row, or refreshes the one with the same `(source, sourceId)`.
     * Returns the stored row: on a refresh it keeps the existing id and `createdAt`.
     * Throws `ConflictException` if another catalog row already holds the ISBN.
     */
    fun upsertCatalogEntry(book: Book): Book
}
