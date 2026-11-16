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
        val composite = CompositeBookMetadataSource(
            sources = registrations.orderedStream().map { it.source }.toList(),
            searchOrder = properties.order.search.map(SourceId::of),
            isbnOrder = properties.order.isbn.map(SourceId::of),
        )

        log.info(
            "Book sources: search {}, ISBN lookup {}",
            composite.searchProviders.map { it.descriptor.id },
            composite.isbnProviders.map { it.descriptor.id },
        )
        if (composite.searchProviders.isEmpty() && composite.isbnProviders.isEmpty()) {
            log.warn("No book source is enabled: search and scan answer from the catalog only")
        }

        return composite
    }
}
