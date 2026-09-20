package com.sauerwein.booktracker.adapter.persistence.book

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.user.UserId
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.stereotype.Component

@Component
class JdbcBookRepository(
    private val template: JdbcAggregateTemplate,
    private val bookQueries: BookQueries,
) : BookRepository {
    override fun add(book: Book) {
        template.insert(book.toEntity())
    }

    override fun update(book: Book) {
        template.update(book.toEntity())
    }

    override fun findVisible(id: BookId, user: UserId): Book? {
        return bookQueries.findVisible(id = id.value, ownerId = user.value)?.toDomain()
    }

    override fun findAllVisible(user: UserId): List<Book> {
        return bookQueries.findAllVisible(ownerId = user.value).map { it.toDomain() }
    }
}
