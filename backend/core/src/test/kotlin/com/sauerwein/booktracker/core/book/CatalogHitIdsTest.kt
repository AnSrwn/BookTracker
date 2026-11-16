package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.Isbn
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
    private val record = CatalogBookRef.SourceRecord(source, recordId)
    private val byIsbn = CatalogBookRef.ByIsbn(Isbn.of("9783570402931"))

    @Test
    fun `reads back the source record it was made from`() {
        val id = hitIds.encode(record)

        assertEquals(record, hitIds.decode(id))
    }

    /** D28: the app must not learn the provider from the id, not even at a glance. */
    @Test
    fun `is opaque and safe in a URL path`() {
        val id = hitIds.encode(record)

        assertTrue(id.startsWith("h2."))
        assertFalse("open_library" in id)
        assertFalse("OL22650690M" in id)
        assertTrue(id.all { it.isLetterOrDigit() || it in "-_." })
    }

    @Test
    fun `rejects an id whose payload was changed`() {
        val (prefix, _, tag) = hitIds.encode(record).split('.')
        val forgedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("r:open_library:OL1M".toByteArray())

        assertNull(hitIds.decode("$prefix.$forgedPayload.$tag"))
    }

    @Test
    fun `rejects an id whose signature was changed`() {
        val (prefix, payload, tag) = hitIds.encode(record).split('.')
        val flipped = (if (tag.first() == 'A') 'B' else 'A') + tag.drop(1)

        assertNull(hitIds.decode("$prefix.$payload.$flipped"))
    }

    @Test
    fun `rejects an id signed with another secret`() {
        val other = CatalogHitIds(ByteArray(32) { 7 })

        assertNull(hitIds.decode(other.encode(record)))
    }

    @Test
    fun `rejects garbage instead of throwing`() {
        listOf("", "h2", "h2..", "h1.a.b", "h2.!!!.???", "not-an-id", UUID.randomUUID().toString())
            .forEach { assertNull(hitIds.decode(it), "accepted '$it'") }
    }

    @Test
    fun `reads back the ISBN it was made from`() {
        assertEquals(byIsbn, hitIds.decode(hitIds.encode(byIsbn)))
    }

    @Test
    fun `an ISBN id does not show the ISBN`() {
        assertFalse("9783570402931" in hitIds.encode(byIsbn))
    }

    /** The kind is signed with the payload: rewriting one kind into the other breaks the signature. */
    @Test
    fun `one kind cannot be passed off as the other`() {
        val (prefix, _, tag) = hitIds.encode(byIsbn).split('.')
        val asRecord = Base64.getUrlEncoder().withoutPadding().encodeToString("r:i:9783570402931".toByteArray())

        assertNull(hitIds.decode("$prefix.$asRecord.$tag"))
    }

    /** Ids of the 9.6 format are no longer valid; nothing was deployed with them. */
    @Test
    fun `rejects an id of the old format`() {
        val (_, payload, tag) = hitIds.encode(record).split('.')

        assertNull(hitIds.decode("h1.$payload.$tag"))
    }

    @Test
    fun `refuses a short secret`() {
        assertFailsWith<IllegalArgumentException> { CatalogHitIds(ByteArray(16)) }
    }
}
