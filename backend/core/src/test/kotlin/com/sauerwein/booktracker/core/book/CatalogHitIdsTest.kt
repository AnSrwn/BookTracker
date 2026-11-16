package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogHitIdsTest {

    private val hitIds = CatalogHitIds(ByteArray(32) { it.toByte() })
    private val source = SourceId.of("open_library")
    private val recordId = SourceRecordId("OL22650690M")

    @Test
    fun `reads back the source record it was made from`() {
        val id = hitIds.encode(source, recordId)

        assertEquals(CatalogBookRef.SourceRecord(source, recordId), hitIds.decode(id))
    }

    /** D28: the app must not learn the provider from the id, not even at a glance. */
    @Test
    fun `is opaque and safe in a URL path`() {
        val id = hitIds.encode(source, recordId)

        assertTrue(id.startsWith("h1."))
        assertFalse("open_library" in id)
        assertFalse("OL22650690M" in id)
        assertTrue(id.all { it.isLetterOrDigit() || it in "-_." })
    }

    @Test
    fun `rejects an id whose payload was changed`() {
        val (prefix, _, tag) = hitIds.encode(source, recordId).split('.')
        val forgedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("open_library:OL1M".toByteArray())

        assertNull(hitIds.decode("$prefix.$forgedPayload.$tag"))
    }

    @Test
    fun `rejects an id whose signature was changed`() {
        val (prefix, payload, tag) = hitIds.encode(source, recordId).split('.')
        val flipped = (if (tag.first() == 'A') 'B' else 'A') + tag.drop(1)

        assertNull(hitIds.decode("$prefix.$payload.$flipped"))
    }

    @Test
    fun `rejects an id signed with another secret`() {
        val other = CatalogHitIds(ByteArray(32) { 7 })

        assertNull(hitIds.decode(other.encode(source, recordId)))
    }

    @Test
    fun `rejects garbage instead of throwing`() {
        listOf("", "h1", "h1..", "h2.a.b", "h1.!!!.???", "not-an-id", UUID.randomUUID().toString())
            .forEach { assertNull(hitIds.decode(it), "accepted '$it'") }
    }

    @Test
    fun `refuses a short secret`() {
        assertFailsWith<IllegalArgumentException> { CatalogHitIds(ByteArray(16)) }
    }
}
