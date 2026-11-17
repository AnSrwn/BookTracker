package com.sauerwein.booktracker.config

import com.sauerwein.booktracker.adapter.bookdata.BookDataProperties
import com.sauerwein.booktracker.adapter.bookdata.MergeField
import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.SourceId
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val BOOKDATA = "booktracker.bookdata"

private val OPEN_LIBRARY = SourceId.of("open_library")
private val DNB = SourceId.of("dnb")
private val GOOGLE_BOOKS = SourceId.of("google_books")
private val GERMAN = Language.of("de")

/**
 * Pins how the shipped `application.yaml` binds the source order and the field precedence —
 * "DNB first for German-language editions" must not be lost silently to a renamed key or property
 * (review F6). A plain unit test: Spring Boot's `Binder` as a library, no application context.
 */
class BookDataPrecedenceBindingTest {

    private fun bindOrder(binder: Binder): BookDataProperties.Order =
        binder.bind("$BOOKDATA.order", BookDataProperties.Order::class.java).get()

    private fun bindPrecedence(binder: Binder): BookDataProperties.PrecedenceProperties =
        binder.bind("$BOOKDATA.precedence", BookDataProperties.PrecedenceProperties::class.java).get()

    private fun shipped(): Binder {
        val sources = YamlPropertySourceLoader().load("application.yaml", ClassPathResource("application.yaml"))
        return Binder(ConfigurationPropertySources.from(sources))
    }

    @Test
    fun `the shipped configuration lists Google's search hits first`() {
        assertEquals(listOf(GOOGLE_BOOKS, OPEN_LIBRARY), bindOrder(shipped()).search.map(SourceId::of))
    }

    @Test
    fun `the shipped configuration lets DNB decide German-language editions and the language`() {
        val precedence = bindPrecedence(shipped()).toMergePrecedence()

        assertEquals(listOf(DNB, OPEN_LIBRARY, GOOGLE_BOOKS), precedence.forLanguage())
        assertEquals(listOf(DNB, GOOGLE_BOOKS, OPEN_LIBRARY), precedence.forField(MergeField.TITLE, GERMAN))
        assertEquals(listOf(GOOGLE_BOOKS, OPEN_LIBRARY), precedence.forField(MergeField.COVER, GERMAN))
    }

    @Test
    fun `the shipped configuration asks DNB last for every other language`() {
        val precedence = bindPrecedence(shipped()).toMergePrecedence()

        assertEquals(listOf(GOOGLE_BOOKS, OPEN_LIBRARY, DNB), precedence.forField(MergeField.PAGE_COUNT, Language.of("en")))
        assertEquals(listOf(GOOGLE_BOOKS, OPEN_LIBRARY, DNB), precedence.forField(MergeField.TITLE, null))
        assertEquals(listOf(OPEN_LIBRARY, GOOGLE_BOOKS), precedence.forField(MergeField.COVER, Language.of("en")))
    }

    @Test
    fun `a language key is normalised like any language`() {
        val source = MapConfigurationPropertySource(
            mapOf("$BOOKDATA.precedence.by-language.ger.fields[0]" to "dnb"),
        )

        val precedence = bindPrecedence(Binder(source)).toMergePrecedence()

        assertEquals(listOf(DNB), precedence.forField(MergeField.TITLE, GERMAN))
    }

    @Test
    fun `a key that is not a language fails startup with its name`() {
        val source = MapConfigurationPropertySource(
            mapOf("$BOOKDATA.precedence.by-language.german.fields[0]" to "dnb"),
        )

        val e = assertFailsWith<IllegalStateException> { bindPrecedence(Binder(source)).toMergePrecedence() }
        assertEquals(true, "by-language.german" in e.message.orEmpty(), e.message)
    }

    @Test
    fun `without a language list, the language follows the default fields`() {
        val source = MapConfigurationPropertySource(
            mapOf("$BOOKDATA.precedence.default.fields[0]" to "open_library"),
        )

        assertEquals(listOf(OPEN_LIBRARY), bindPrecedence(Binder(source)).toMergePrecedence().forLanguage())
    }
}
