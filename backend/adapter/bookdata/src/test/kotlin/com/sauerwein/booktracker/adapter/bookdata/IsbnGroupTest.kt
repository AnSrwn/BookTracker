package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsbnGroupTest {

    @Test
    fun `978-3 is the German-language group`() {
        assertEquals(IsbnGroup.GERMAN_LANGUAGE, IsbnGroup.of(Isbn.of("9783570402931")))
        // An ISBN-10 starting with 3 is the same group once normalised
        assertEquals(IsbnGroup.GERMAN_LANGUAGE, IsbnGroup.of(Isbn.of("3570402932")))
    }

    @Test
    fun `other groups have no order of their own`() {
        assertNull(IsbnGroup.of(Isbn.of("9780735619678")))   // English
        assertNull(IsbnGroup.of(Isbn.of("9788082035097")))   // Slovak
        assertNull(IsbnGroup.of(Isbn.of("9791032305690")))   // 979-10, French
    }

    /** Review F5a: an empty group list must not turn into "sorted by id". */
    @Test
    fun `an absent or empty group list in the configuration uses the default order`() {
        val default = listOf("open_library", "dnb")

        listOf(null, emptyList<String>()).forEach { germanLanguage ->
            val order = BookDataProperties.IsbnOrderProperties(default = default, germanLanguage = germanLanguage).toIsbnOrder()
            assertEquals(default.map(SourceId::of), order.forGroup(IsbnGroup.GERMAN_LANGUAGE))
            assertEquals(emptyMap(), order.byGroup)
        }
    }

    @Test
    fun `a configured group list becomes that group's order`() {
        val order = BookDataProperties.IsbnOrderProperties(
            default = listOf("open_library", "dnb"),
            germanLanguage = listOf("dnb", "open_library"),
        ).toIsbnOrder()

        assertEquals(listOf(SourceId.of("dnb"), SourceId.of("open_library")), order.forGroup(IsbnGroup.GERMAN_LANGUAGE))
        assertEquals(listOf(SourceId.of("open_library"), SourceId.of("dnb")), order.forGroup(null))
    }

    @Test
    fun `a group without its own list uses the default order`() {
        val default = listOf(SourceId.of("library"))
        val order = IsbnOrder(default = default)

        assertEquals(default, order.forGroup(IsbnGroup.GERMAN_LANGUAGE))
        assertEquals(default, order.forGroup(null))
    }
}
