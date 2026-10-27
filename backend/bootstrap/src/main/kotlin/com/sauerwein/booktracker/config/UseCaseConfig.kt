package com.sauerwein.booktracker.config

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.usecases.*
import com.sauerwein.booktracker.core.user.CurrentUserProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class UseCaseConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun addBookUseCase(
        bookRepository: BookRepository,
        currentUserProvider: CurrentUserProvider,
        clock: Clock,
    ) = AddBookUseCase(
        bookRepository = bookRepository,
        currentUserProvider = currentUserProvider,
        clock = clock
    )

    @Bean
    fun getBookUseCase(bookRepository: BookRepository, currentUserProvider: CurrentUserProvider) = GetBookUseCase(
        bookRepository = bookRepository,
        currentUserProvider = currentUserProvider,
    )

    @Bean
    fun getBooksUseCase(bookRepository: BookRepository, currentUserProvider: CurrentUserProvider) = GetBooksUseCase(
        bookRepository = bookRepository,
        currentUserProvider = currentUserProvider,
    )

    @Bean
    fun findBookByIsbnUseCase(bookMetadataSource: BookMetadataSource) = FindBookByIsbnUseCase(
        bookMetadataSource = bookMetadataSource,
    )

    @Bean
    fun searchCatalogUseCase(bookMetadataSource: BookMetadataSource) = SearchCatalogUseCase(
        bookMetadataSource = bookMetadataSource,
    )
}
