package com.sauerwein.booktracker.config

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.usecases.*
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
    fun createPrivateBookUseCase(
        bookRepository: BookRepository,
        currentUserProvider: CurrentUserProvider,
        clock: Clock,
    ) = CreatePrivateBookUseCase(
        bookRepository = bookRepository,
        currentUserProvider = currentUserProvider,
        clock = clock,
    )

    @Bean
    fun addCatalogBookUseCase(
        bookRepository: BookRepository,
        currentUserProvider: CurrentUserProvider,
        clock: Clock,
    ) = AddCatalogBookUseCase(
        bookRepository = bookRepository,
        currentUserProvider = currentUserProvider,
        clock = clock,
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
    fun upsertCatalogEntryUseCase(bookRepository: BookRepository, clock: Clock) = UpsertCatalogEntryUseCase(
        bookRepository = bookRepository,
        clock = clock,
    )

    @Bean
    fun resolveIsbnUseCase(
        bookRepository: BookRepository,
        bookMetadataSource: BookMetadataSource,
        upsertCatalogEntryUseCase: UpsertCatalogEntryUseCase,
    ) = ResolveIsbnUseCase(
        bookRepository = bookRepository,
        bookMetadataSource = bookMetadataSource,
        upsertCatalogEntryUseCase = upsertCatalogEntryUseCase,
    )

    @Bean
    fun findBookByIsbnUseCase(resolveIsbnUseCase: ResolveIsbnUseCase) = FindBookByIsbnUseCase(
        resolveIsbnUseCase = resolveIsbnUseCase,
    )

    @Bean
    fun searchCatalogUseCase(
        resolveIsbnUseCase: ResolveIsbnUseCase,
        upsertCatalogEntryUseCase: UpsertCatalogEntryUseCase,
        bookRepository: BookRepository,
        bookMetadataSource: BookMetadataSource,
        currentUserProvider: CurrentUserProvider,
        @Value($$"${booktracker.catalog.min-local-hits}") minLocalHits: Int,
    ) = SearchCatalogUseCase(
        resolveIsbnUseCase = resolveIsbnUseCase,
        upsertCatalogEntryUseCase = upsertCatalogEntryUseCase,
        bookRepository = bookRepository,
        bookMetadataSource = bookMetadataSource,
        currentUserProvider = currentUserProvider,
        minLocalHits = minLocalHits,
    )
}
