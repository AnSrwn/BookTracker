package com.sauerwein.booktracker.adapter.persistence.book

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.util.*

interface BookQueries : Repository<BookEntity, UUID> {

    @Query("SELECT * FROM book WHERE owner_id IS NULL OR owner_id = :ownerId")
    fun findAllVisible(@Param("ownerId") ownerId: UUID): List<BookEntity>

    @Query("SELECT * FROM book WHERE id = :id AND (owner_id IS NULL OR owner_id = :ownerId)")
    fun findVisible(@Param("id") id: UUID, @Param("ownerId") ownerId: UUID): BookEntity?
}
