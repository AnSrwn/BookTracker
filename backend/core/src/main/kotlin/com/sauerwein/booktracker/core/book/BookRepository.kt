package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.user.UserId

/**
 * Every read is either scoped to the caller or restricted to the approved public catalog
 * (ADR-006). Writes throw `ConflictException` on a duplicate ISBN or catalog identity.
 */
interface BookRepository {
    fun add(book: Book)
    fun update(book: Book)
    fun findVisible(id: BookId, user: UserId): Book?
    fun findAllVisible(user: UserId): List<Book>

    /** Public, approved rows only — a user's private row is their edit, not a source's answer. */
    fun findCatalogByIsbn(isbn: Isbn): Book?

    /** Every word of [text] must appear in the title or an author name. */
    fun searchVisible(text: String, user: UserId, limit: Int): List<Book>

    /**
     * Inserts a public catalog row, or refreshes the one with the same `(source, sourceId)`.
     * Returns the stored row: on a refresh it keeps the existing id and `createdAt`.
     */
    fun upsertCatalogEntry(book: Book): Book
}
