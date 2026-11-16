package com.sauerwein.booktracker.adapter.persistence.book

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.util.*

/** The caller's own rows, plus the approved public catalog. Every user-scoped read uses it. */
private const val VISIBLE = "(owner_id = :ownerId OR (owner_id IS NULL AND review_state = 'APPROVED'))"
private const val APPROVED_CATALOG = "(owner_id IS NULL AND review_state = 'APPROVED')"

/**
 * `simple`: no stemming and no stop words, so German and English titles behave alike.
 * Whole words only — a partial word misses locally and falls through to the external sources.
 */
private const val MATCHES_TEXT = """
    to_tsvector('simple', title || ' ' || coalesce((SELECT string_agg(a.name, ' ') FROM book_author a WHERE a.book_id = book.id), ''))
        @@ plainto_tsquery('simple', :text)"""

interface BookQueries : Repository<BookEntity, UUID> {

    /** Only what is linked to the user — never the whole catalog, unlike [VISIBLE]. */
    @Query(
        """SELECT book.* FROM book
           JOIN user_book ub ON ub.book_id = book.id
           WHERE ub.user_id = :userId
           ORDER BY ub.created_at DESC"""
    )
    fun findLibrary(@Param("userId") userId: UUID): List<BookEntity>

    @Query("SELECT * FROM book WHERE id = :id AND $VISIBLE")
    fun findVisible(@Param("id") id: UUID, @Param("ownerId") ownerId: UUID): BookEntity?

    @Query("SELECT * FROM book WHERE isbn = :isbn AND $APPROVED_CATALOG")
    fun findCatalogByIsbn(@Param("isbn") isbn: String): BookEntity?

    @Query("SELECT * FROM book WHERE $VISIBLE AND $MATCHES_TEXT ORDER BY title LIMIT :limit")
    fun searchVisible(
        @Param("text") text: String,
        @Param("ownerId") ownerId: UUID,
        @Param("limit") limit: Int,
    ): List<BookEntity>
}
