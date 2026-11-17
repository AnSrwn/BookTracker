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
        val precedence = properties.precedence.toMergePrecedence()
        val composite = CompositeBookMetadataSource(
            sources = sources,
            searchOrder = searchOrder,
            merge = BookCandidateMerge(precedence),
        )

        val lists = mapOf("order.search" to searchOrder) + precedence.all.mapKeys { (path, _) -> "precedence.$path" }
        lists.forEach { (path, order) ->
            val unmatched = CompositeBookMetadataSource.unmatchedOrderIds(order, sources)
            if (unmatched.isNotEmpty()) {
                log.warn(
                    "booktracker.bookdata.{} lists {}, which matches no running source (disabled or misspelled)",
                    path,
                    unmatched,
                )
            }
        }

        log.info(
            "Book sources: search {}, ISBN lookup {} (asked in parallel, merged)",
            composite.searchProviders.map { it.descriptor.id },
            composite.isbnProviders.map { it.descriptor.id },
        )
        log.info(
            "Book field precedence: {}",
            precedence.all.filterValues { it.isNotEmpty() }.entries.joinToString { (path, order) -> "$path $order" },
        )
        if (composite.searchProviders.isEmpty() && composite.isbnProviders.isEmpty()) {
            log.warn("No book source is enabled: search and scan answer from the catalog only")
        }

        return composite
    }
}
