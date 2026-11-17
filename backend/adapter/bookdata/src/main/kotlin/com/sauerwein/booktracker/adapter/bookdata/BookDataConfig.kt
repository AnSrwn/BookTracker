package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.models.SourceId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(BookDataProperties::class)
class BookDataConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    /** The only [BookMetadataSource] in the context; the providers join it as registrations. */
    @Bean
    fun bookMetadataSource(
        registrations: ObjectProvider<BookSourceRegistration>,
        properties: BookDataProperties,
    ): BookMetadataSource {
        val sources = registrations.orderedStream().map { it.source }.toList()
        val searchOrder = properties.order.search.map(SourceId::of)
        val isbnOrder = properties.order.isbn.toIsbnOrder()
        val composite = CompositeBookMetadataSource(sources = sources, searchOrder = searchOrder, isbnOrder = isbnOrder)

        val orders =
            mapOf("search" to searchOrder) + isbnOrder.all.mapKeys { (name, _) -> "isbn.${name.replace('_', '-')}" }
        orders.forEach { (list, order) ->
            val unmatched = CompositeBookMetadataSource.unmatchedOrderIds(order, sources)
            if (unmatched.isNotEmpty()) {
                log.warn(
                    "booktracker.bookdata.order.{} lists {}, which matches no running source (disabled or misspelled)",
                    list,
                    unmatched
                )
            }
        }

        val isbnLookup = mapOf(IsbnOrder.DEFAULT_NAME to composite.isbnProviders) +
                composite.isbnProvidersByGroup.mapKeys { (group, _) -> group.name.lowercase() }
        log.info(
            "Book sources: search {}, ISBN lookup {}",
            composite.searchProviders.map { it.descriptor.id },
            isbnLookup.entries.joinToString { (name, providers) -> "$name ${providers.map { it.descriptor.id }}" },
        )
        if (composite.searchProviders.isEmpty() && composite.isbnProviders.isEmpty()) {
            log.warn("No book source is enabled: search and scan answer from the catalog only")
        }

        return composite
    }
}
