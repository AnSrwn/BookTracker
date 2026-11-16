package com.sauerwein.booktracker.config

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.usecases.*
import com.sauerwein.booktracker.core.book.usecases.ResolveIsbnUseCase
import com.sauerwein.booktracker.core.user.CurrentUserProvider
import org.springframework.beans.factory.annotation.Value
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
    fun catalogUpsertUseCase(bookRepository: BookRepository, clock: Clock) = CatalogUpsertUseCase(
        bookRepository = bookRepository,
        clock = clock,
    )

    @Bean
    fun resolveIsbnUseCase(
        bookRepository: BookRepository,
        bookMetadataSource: BookMetadataSource,
        catalogUpsertUseCase: CatalogUpsertUseCase,
    ) = ResolveIsbnUseCase(
        bookRepository = bookRepository,
        bookMetadataSource = bookMetadataSource,
        catalogUpsertUseCase = catalogUpsertUseCase,
    )

    @Bean
    fun findBookByIsbnUseCase(resolveIsbnUseCase: ResolveIsbnUseCase) = FindBookByIsbnUseCase(
        resolveIsbnUseCase = resolveIsbnUseCase,
    )

    @Bean
    fun searchCatalogUseCase(
        resolveIsbnUseCase: ResolveIsbnUseCase,
        bookRepository: BookRepository,
        bookMetadataSource: BookMetadataSource,
        currentUserProvider: CurrentUserProvider,
        @Value("\${booktracker.catalog.min-local-hits}") minLocalHits: Int,
    ) = SearchCatalogUseCase(
        resolveIsbnUseCase = resolveIsbnUseCase,
        bookRepository = bookRepository,
        bookMetadataSource = bookMetadataSource,
        currentUserProvider = currentUserProvider,
        minLocalHits = minLocalHits,
    )
}
