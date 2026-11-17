package com.sauerwein.booktracker.config

import com.sauerwein.booktracker.adapter.bookdata.BookDataProperties
import com.sauerwein.booktracker.adapter.bookdata.IsbnGroup
import com.sauerwein.booktracker.adapter.bookdata.IsbnOrder
import com.sauerwein.booktracker.core.book.models.SourceId
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ORDER = "booktracker.bookdata.order"

private val OPEN_LIBRARY = SourceId.of("open_library")
private val DNB = SourceId.of("dnb")
private val GOOGLE_BOOKS = SourceId.of("google_books")

/**
 * Pins how the shipped `application.yaml` binds the source order — "DNB first for 978-3" must not be
 * lost silently to a renamed key or property (review F6). A plain unit test: Spring Boot's `Binder`
 * as a library, no application context.
 */
class BookDataOrderBindingTest {

    private fun bind(binder: Binder): BookDataProperties.Order =
        binder.bind(ORDER, BookDataProperties.Order::class.java).get()

    @Test
    fun `the shipped configuration asks DNB first for German-language ISBNs and last for the rest`() {
        val sources = YamlPropertySourceLoader().load("application.yaml", ClassPathResource("application.yaml"))
        val order = bind(Binder(ConfigurationPropertySources.from(sources)))

        assertEquals(listOf(GOOGLE_BOOKS, OPEN_LIBRARY), order.search.map(SourceId::of))
        assertEquals(
            IsbnOrder(
                default = listOf(OPEN_LIBRARY, DNB),
                byGroup = mapOf(IsbnGroup.GERMAN_LANGUAGE to listOf(DNB, OPEN_LIBRARY)),
            ),
            order.isbn.toIsbnOrder(),
        )
    }

    @Test
    fun `without a German-language list, German-language ISBNs use the default order`() {
        val source = MapConfigurationPropertySource(mapOf("$ORDER.isbn.default[0]" to "open_library"))
        val order = bind(Binder(source)).isbn.toIsbnOrder()

        assertEquals(listOf(OPEN_LIBRARY), order.forGroup(IsbnGroup.GERMAN_LANGUAGE))
    }
}
