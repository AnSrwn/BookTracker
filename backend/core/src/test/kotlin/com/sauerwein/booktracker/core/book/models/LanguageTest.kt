package com.sauerwein.booktracker.core.book.models

import com.sauerwein.booktracker.core.shared.exception.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LanguageTest {

    @Test
    fun `keeps a two-letter code`() {
        assertEquals("de", Language.of("de").value)
        assertEquals("en", Language.of("EN").value)
    }

    /** A full BCP 47 tag, as a phone's locale sends it, keeps only its language (9.9 Q9). */
    @Test
    fun `drops region and script from a full tag`() {
        assertEquals("de", Language.of("de-AT").value)
        assertEquals("zh", Language.of("zh-Hant-TW").value)
        assertEquals("pt", Language.of("pt_BR").value)
    }

    /** DNB and Open Library send bibliographic ISO 639-2 codes; both spellings of a language end up equal. */
    @Test
    fun `maps the three-letter codes libraries send to the two-letter code`() {
        assertEquals("de", Language.of("ger").value)
        assertEquals("de", Language.of("deu").value)
        assertEquals("fr", Language.of("fre").value)
        assertEquals("nl", Language.of("dut").value)
        assertEquals("zh", Language.of("chi").value)
        assertEquals("en", Language.of("eng").value)
        assertEquals("sk", Language.of("slo").value)
    }

    /** 9.9 Q15: any well-formed three-letter code passes; there is no list of them to check against. */
    @Test
    fun `keeps a three-letter code that has no two-letter one`() {
        assertEquals("haw", Language.of("haw").value)
        assertEquals("foo", Language.of("foo").value)
    }

    /** 9.9 review F5: only the language is kept, but the whole tag must be one. */
    @Test
    fun `accepts a well-formed tag with region, script or variant`() {
        assertEquals("de", Language.of("de-CH-1996").value)
        assertEquals("sr", Language.of("sr-Latn").value)
    }

    @Test
    fun `rejects what is not a language`() {
        listOf("", " ", "x", "xx", "xx-!!", "german", "de1", "und", "mul", "zxx", "mis", "qab", "de-!!", "de_x y", "en-zzzzzzzzzzzz")
            .forEach { raw ->
                val e = assertFailsWith<ValidationException>("accepted '$raw'") { Language.of(raw) }
                assertEquals("language", e.field)
                assertEquals("InvalidLanguage", e.code)
            }
    }

    @Test
    fun `ofOrNull is null for an unreadable or missing code`() {
        assertNull(Language.ofOrNull("und"))
        assertNull(Language.ofOrNull(null))
        assertEquals(Language.of("de"), Language.ofOrNull("ger"))
    }
}
