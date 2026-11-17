package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogHitIdsTest {

    private val secret = ByteArray(32) { it.toByte() }
    private val hitIds = CatalogHitIds(secret)
    private val source = SourceId.of("open_library")
    private val recordId = SourceRecordId("OL22650690M")
    private val record = CatalogBookRef.ByIsbn(Isbn.of("9783570402931"), SourceRef(source, recordId))
    private val byIsbn = CatalogBookRef.ByIsbn(Isbn.of("9783570402931"))

    /** A correctly signed id for any payload, to prove the decoder rejects it for its content alone. */
    private fun signed(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val tag = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
            .doFinal(payload.toByteArray()).copyOf(16)
        return "h2.${encoder.encodeToString(payload.toByteArray())}.${encoder.encodeToString(tag)}"
    }

    @Test
    fun `reads back the ISBN and the source record it was made from`() {
        val id = hitIds.encode(record)

        assertEquals(record, hitIds.decode(id))
    }

    @Test
    fun `a record id containing the separator survives the round trip`() {
        val withColon = record.copy(record = SourceRef(source, SourceRecordId("a:b:c")))

        assertEquals(withColon, hitIds.decode(hitIds.encode(withColon)))
    }

    /** 9.9 I13: the record kind is gone. Even correctly signed, such an id is not read. */
    @Test
    fun `rejects the former record kind even when signed`() {
        assertNull(hitIds.decode(signed("r:open_library:OL22650690M")))
        assertEquals(byIsbn, hitIds.decode(signed("i:9783570402931")), "the signing helper must match the real one")
    }

    @Test
    fun `rejects an ISBN id with an incomplete record`() {
        assertNull(hitIds.decode(signed("i:9783570402931:open_library")))
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
            .encodeToString("i:9783570402931:open_library:OL1M".toByteArray())

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

    /** The fallback record is signed with the ISBN: adding one to an ISBN-only id breaks the signature. */
    @Test
    fun `a record cannot be attached to an ISBN-only id`() {
        val (prefix, _, tag) = hitIds.encode(byIsbn).split('.')
        val withRecord = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("i:9783570402931:open_library:OL1M".toByteArray())

        assertNull(hitIds.decode("$prefix.$withRecord.$tag"))
    }

    @Test
    fun `neither the ISBN nor the record shows in the id`() {
        val id = hitIds.encode(record)

        assertFalse("9783570402931" in id)
        assertFalse("OL22650690M" in id)
        assertFalse("open_library" in id)
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
