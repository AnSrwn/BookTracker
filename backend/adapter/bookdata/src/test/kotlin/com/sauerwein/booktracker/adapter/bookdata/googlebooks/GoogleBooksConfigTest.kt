package com.sauerwein.booktracker.adapter.bookdata.googlebooks

import com.sauerwein.booktracker.adapter.bookdata.ProviderProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** E9 / D3: enabled without a key must stop the start, naming what to set. */
class GoogleBooksConfigTest {

    private val provider = ProviderProperties(enabled = true, baseUrl = "https://www.googleapis.com")

    @Test
    fun `refuses to run without a key`() {
        listOf(null, "", "   ").forEach { key ->
            val e = assertFailsWith<IllegalStateException> { requireGoogleBooksApiKey(provider.copy(apiKey = key)) }

            assertTrue(GOOGLE_BOOKS_API_KEY_VARIABLE in e.message.orEmpty(), e.message)
            assertTrue("enabled=false" in e.message.orEmpty(), e.message)
        }
    }

    @Test
    fun `passes a key through`() {
        assertEquals("abc", requireGoogleBooksApiKey(provider.copy(apiKey = "abc")))
    }
}
