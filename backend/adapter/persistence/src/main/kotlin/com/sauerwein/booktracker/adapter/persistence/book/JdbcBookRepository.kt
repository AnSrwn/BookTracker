package com.sauerwein.booktracker.adapter.persistence.book

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.shared.exception.ConflictException
import com.sauerwein.booktracker.core.user.UserId
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.*

private const val DUPLICATE_BOOK = "DuplicateBook"
private const val ALREADY_IN_LIBRARY = "AlreadyInLibrary"

private const val INSERT_LINK = """
    INSERT INTO user_book (user_id, book_id, created_at)
    VALUES (:userId, :bookId, :createdAt)"""

/** Spring Data JDBC has no upsert; ON CONFLICT is the native-SQL path ADR-002 leaves open. */
private const val UPSERT_CATALOG_ENTRY = """
    INSERT INTO book (id, owner_id, title, publisher, isbn, publication_year, page_count, cover_url,
                      source, source_id, source_fetched_at, work_key, created_at, updated_at)
    VALUES (:id, NULL, :title, :publisher, :isbn, :publicationYear, :pageCount, :coverUrl,
            :source, :sourceId, :sourceFetchedAt, :workKey, :createdAt, :updatedAt)
    ON CONFLICT (source, source_id) WHERE owner_id IS NULL DO UPDATE SET
        title = EXCLUDED.title,
        publisher = EXCLUDED.publisher,
        isbn = EXCLUDED.isbn,
        publication_year = EXCLUDED.publication_year,
        page_count = EXCLUDED.page_count,
        cover_url = EXCLUDED.cover_url,
        source_fetched_at = EXCLUDED.source_fetched_at,
        work_key = EXCLUDED.work_key,
        updated_at = EXCLUDED.updated_at
    RETURNING id, created_at"""

private const val DELETE_AUTHORS = "DELETE FROM book_author WHERE book_id = :bookId"

private const val INSERT_AUTHOR = """
    INSERT INTO book_author (book_id, position, name, source, source_author_id)
    VALUES (:bookId, :position, :name, :source, :sourceAuthorId)"""

/**
 * Every write is a book plus its authors, so each runs in its own transaction —
 * `JdbcAggregateTemplate` is not transactional by itself.
 */
@Component
class JdbcBookRepository(
    private val template: JdbcAggregateTemplate,
    private val bookQueries: BookQueries,
    private val jdbcClient: JdbcClient,
    private val transactionTemplate: TransactionTemplate,
) : BookRepository {
    override fun addPrivateBook(book: Book) {
        val owner = requireNotNull(book.ownerId) { "A private book needs an owner" }
        write(conflictCode = DUPLICATE_BOOK) {
            template.insert(book.toEntity())
            insertLink(userId = owner.value, bookId = book.id.value, createdAt = book.createdAt)
        }
    }

    override fun update(book: Book) {
        write(conflictCode = DUPLICATE_BOOK) { template.update(book.toEntity()) }
    }

    override fun findVisible(id: BookId, user: UserId): Book? {
        return bookQueries.findVisible(id = id.value, ownerId = user.value)?.toDomain()
    }

    override fun findLibrary(user: UserId): List<Book> {
        return bookQueries.findLibrary(userId = user.value).map { it.toDomain() }
    }

    override fun addToLibrary(user: UserId, bookId: BookId, at: Instant) {
        write(conflictCode = ALREADY_IN_LIBRARY) {
            insertLink(
                userId = user.value,
                bookId = bookId.value,
                createdAt = at
            )
        }
    }

    private fun insertLink(userId: UUID, bookId: UUID, createdAt: Instant): Int =
        jdbcClient.sql(INSERT_LINK)
            .param("userId", userId)
            .param("bookId", bookId)
            .param("createdAt", Timestamp.from(createdAt))
            .update()

    override fun findCatalogByIsbn(isbn: Isbn): Book? {
        return bookQueries.findCatalogByIsbn(isbn = isbn.value)?.toDomain()
    }

    override fun searchVisible(text: String, user: UserId, limit: Int): List<Book> {
        return bookQueries.searchVisible(text = text, ownerId = user.value, limit = limit).map { it.toDomain() }
    }

    override fun upsertCatalogEntry(book: Book): Book = write(conflictCode = DUPLICATE_BOOK) {
        require(book.ownerId == null) { "Only public catalog rows are upserted" }
        val entity = book.toEntity()

        val (id, createdAt) = jdbcClient.sql(UPSERT_CATALOG_ENTRY)
            .param("id", entity.id)
            .param("title", entity.title)
            .param("publisher", entity.publisher)
            .param("isbn", entity.isbn)
            .param("publicationYear", entity.publicationYear)
            .param("pageCount", entity.pageCount)
            .param("coverUrl", entity.coverUrl)
            .param("source", entity.source)
            .param("sourceId", entity.sourceId)
            .param("sourceFetchedAt", entity.sourceFetchedAt?.let(Timestamp::from))
            .param("workKey", entity.workKey)
            .param("createdAt", Timestamp.from(entity.createdAt))
            .param("updatedAt", Timestamp.from(entity.updatedAt))
            .query { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getTimestamp("created_at").toInstant() }
            .single()

        replaceAuthors(bookId = id, authors = entity.authors)
        book.copy(id = BookId(id), createdAt = createdAt)
    }

    private fun replaceAuthors(bookId: UUID, authors: List<BookAuthorEntity>) {
        jdbcClient.sql(DELETE_AUTHORS).param("bookId", bookId).update()
        authors.forEachIndexed { position, author ->
            jdbcClient.sql(INSERT_AUTHOR)
                .param("bookId", bookId)
                .param("position", position)
                .param("name", author.name)
                .param("source", author.source)
                .param("sourceAuthorId", author.sourceAuthorId)
                .update()
        }
    }

    /**
     * Only a unique violation is a conflict the caller can act on. A CHECK violation — the
     * public-catalog allowlist above all — means a code path skipped the licence rule: a bug,
     * so it stays a 500.
     */
    private fun <T : Any> write(conflictCode: String, block: () -> T): T {
        try {
            return transactionTemplate.execute { block() }
        } catch (_: DuplicateKeyException) {
            val message = when (conflictCode) {
                ALREADY_IN_LIBRARY -> "Book is already in your books"
                else -> "Book already exists in this collection"
            }
            throw ConflictException(code = conflictCode, message = message)
        }
    }
}
