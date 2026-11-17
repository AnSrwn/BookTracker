package com.sauerwein.booktracker.config

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.CatalogHitIds
import com.sauerwein.booktracker.core.book.BookRepository
import com.sauerwein.booktracker.core.book.usecases.*
import com.sauerwein.booktracker.core.user.CurrentUserProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.SecureRandom
import java.time.Clock

private val log = LoggerFactory.getLogger(UseCaseConfig::class.java)

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
        bookMetadataSource: BookMetadataSource,
        storeCatalogEntryUseCase: StoreCatalogEntryUseCase,
        resolveIsbnUseCase: ResolveIsbnUseCase,
        currentUserProvider: CurrentUserProvider,
        clock: Clock,
    ) = AddCatalogBookUseCase(
        bookRepository = bookRepository,
        bookMetadataSource = bookMetadataSource,
        storeCatalogEntryUseCase = storeCatalogEntryUseCase,
        resolveIsbnUseCase = resolveIsbnUseCase,
        currentUserProvider = currentUserProvider,
        clock = clock,
    )

    /**
     * Signs the ids of search hits not stored yet (D28). Without a configured secret, dev gets a
     * random one per process: hit ids then die with a restart, which is fine locally. Production
     * must set it (compose.prod.yaml), or ids differ between instances and restarts.
     */
    @Bean
    fun catalogHitIds(@Value($$"${booktracker.catalog.hit-id-secret:}") secret: String): CatalogHitIds {
        if (secret.isNotBlank()) return CatalogHitIds(secret.toByteArray(Charsets.UTF_8))

        log.warn("booktracker.catalog.hit-id-secret is not set: using a random secret, search hit ids will not survive a restart")
        return CatalogHitIds(ByteArray(32).also(SecureRandom()::nextBytes))
    }

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
    fun storeCatalogEntryUseCase(bookRepository: BookRepository, clock: Clock) = StoreCatalogEntryUseCase(
        bookRepository = bookRepository,
        clock = clock,
    )

    @Bean
    fun resolveIsbnUseCase(
        bookRepository: BookRepository,
        bookMetadataSource: BookMetadataSource,
        storeCatalogEntryUseCase: StoreCatalogEntryUseCase,
    ) = ResolveIsbnUseCase(
        bookRepository = bookRepository,
        bookMetadataSource = bookMetadataSource,
        storeCatalogEntryUseCase = storeCatalogEntryUseCase,
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
        @Value($$"${booktracker.catalog.min-local-hits}") minLocalHits: Int,
    ) = SearchCatalogUseCase(
        resolveIsbnUseCase = resolveIsbnUseCase,
        bookRepository = bookRepository,
        bookMetadataSource = bookMetadataSource,
        currentUserProvider = currentUserProvider,
        minLocalHits = minLocalHits,
    )
}
