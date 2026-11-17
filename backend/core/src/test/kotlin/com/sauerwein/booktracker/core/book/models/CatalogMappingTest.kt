package com.sauerwein.booktracker.core.book.models

import com.sauerwein.booktracker.core.book.testCandidate
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ISBN = Isbn.of("9783570402931")
private val OPEN_LIBRARY = SourceId.of("open_library")
private val RECORD = SourceRecordId("OL22650690M")

/** How a result can be added (9.9 I13, I15): the rule behind every hit id the app receives. */
class CatalogMappingTest {

    @Test
    fun `a stored book is added by its id`() {
        val id = BookId(UUID.randomUUID())

        assertEquals(CatalogBookRef.Stored(id), testCandidate(isbn = ISBN).copy(bookId = id).addRef)
    }

    /** A hit that may enter the catalog by itself carries its own record as the fallback (D29, Q13). */
    @Test
    fun `a hit from one CC0 source is added by its ISBN, with its own record as the fallback`() {
        val hit = testCandidate(isbn = ISBN, source = OPEN_LIBRARY, sourceId = RECORD)

        assertEquals(CatalogBookRef.ByIsbn(ISBN, SourceRef(OPEN_LIBRARY, RECORD)), hit.addRef)
    }

    /** I15: data that may not be stored — a Google hit, or a hit merged with Google data — has no record to fall back to. */
    @Test
    fun `a hit whose data may not be stored is added by its ISBN alone`() {
        val merged = testCandidate(isbn = ISBN, source = OPEN_LIBRARY, sourceId = RECORD, persistableInPublicCatalog = false)

        assertEquals(CatalogBookRef.ByIsbn(ISBN), merged.addRef)
        assertFalse(merged.isDeadEnd)
    }

    /** D22: without an ISBN a stored row could never match a later scan. Such a hit can only be copied. */
    @Test
    fun `a CC0 hit without an ISBN cannot be added`() {
        val hit = testCandidate(isbn = null, source = OPEN_LIBRARY, sourceId = RECORD)

        assertNull(hit.addRef)
        assertFalse(hit.isDeadEnd)
    }

    /** 9.7 D6, D8: neither addable nor copyable — such a hit is not shown at all. */
    @Test
    fun `a hit whose data may not be stored and that has no ISBN is a dead end`() {
        val hit = testCandidate(isbn = null, persistableInPublicCatalog = false)

        assertNull(hit.addRef)
        assertTrue(hit.isDeadEnd)
    }
}
