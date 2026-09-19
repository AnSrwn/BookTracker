package com.sauerwein.booktracker.adapter.persistence.book

import com.sauerwein.booktracker.core.book.Book
import com.sauerwein.booktracker.core.book.BookId
import com.sauerwein.booktracker.core.book.BookRepository
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class JdbcBookRepository(
    private val template: JdbcAggregateTemplate,
    private val repository: BookDao,
) : BookRepository {
    override fun add(book: Book) {
        template.insert(book.toEntity())
    }

    override fun update(book: Book) {
        template.update(book.toEntity())
    }

    override fun findById(id: BookId): Book? {
        return repository.findByIdOrNull(id.value)?.toDomain()
    }

    override fun findAll(): List<Book> {
        return repository.findAll().map { it.toDomain() }
    }
}
