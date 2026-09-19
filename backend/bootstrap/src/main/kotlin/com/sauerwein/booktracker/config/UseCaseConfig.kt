package com.sauerwein.booktracker.config

import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.usecases.AddBookUseCase
import com.sauerwein.booktracker.core.book.usecases.GetBookUseCase
import com.sauerwein.booktracker.core.book.usecases.GetBooksUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class UseCaseConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun addBookUseCase(bookRepository: BookRepository, clock: Clock) = AddBookUseCase(
        bookRepository = bookRepository,
        clock = clock
    )

    @Bean
    fun getBookUseCase(bookRepository: BookRepository) = GetBookUseCase(bookRepository = bookRepository)

    @Bean
    fun getBooksUseCase(bookRepository: BookRepository) = GetBooksUseCase(bookRepository = bookRepository)
}
