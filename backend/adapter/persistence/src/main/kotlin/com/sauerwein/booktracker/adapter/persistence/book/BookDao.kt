package com.sauerwein.booktracker.adapter.persistence.book

import org.springframework.data.repository.ListCrudRepository
import java.util.*

interface BookDao : ListCrudRepository<BookEntity, UUID>
