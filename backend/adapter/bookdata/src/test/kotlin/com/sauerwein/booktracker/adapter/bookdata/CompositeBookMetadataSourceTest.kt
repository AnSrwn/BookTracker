package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceQuotaExhaustedException
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ISBN = Isbn.of("9783570402931")
private val FINDER = SourceId.of("finder")
private val LIBRARY = SourceId.of("library")
private val ARCHIVE = SourceId.of("archive")

/** Made-up provider ids on purpose: the composite must work for any provider, never a named one. */
class CompositeBookMetadataSourceTest {

    private class FakeProvider(
        id: SourceId,
        search: Boolean = true,
        isbn: Boolean = true,
        private val hits: List<BookCandidate> = emptyList(),
        private val isbnHit: BookCandidate? = null,
        private val failure: RuntimeException? = null,
    ) : BookMetadataSource {
        val calls = mutableListOf<String>()

        override val descriptor = SourceDescriptor(
            id = id,
            supportsIsbnLookup = isbn,
            supportsFreeTextSearch = search,
            providesCovers = false,
            persistableInPublicCatalog = true,
        )

        override fun search(text: String): List<BookCandidate> {
            calls += "search"
            failure?.let { throw it }
            return hits
        }

        override fun findByIsbn(isbn: Isbn): BookCandidate? {
            calls += "isbn"
            failure?.let { throw it }
            return isbnHit
        }

        override fun findByRecordId(ref: SourceRef): BookCandidate? {
            calls += "record"
            return if (ref.source == descriptor.id) hits.firstOrNull() else null
        }
    }

    private fun hit(source: SourceId, title: String) = BookCandidate(
        title = title,
        authors = listOf(Author("Christopher Paolini", ref = null)),
        publisher = null,
        isbn = null,
        publicationYear = null,
        pageCount = null,
        coverUrl = null,
        source = source,
        sourceId = SourceRecordId(title),
        workKey = null,
        persistableInPublicCatalog = true,
    )

    private fun down(id: SourceId) = SourceUnavailableException(id)
    private fun quotaSpent(id: SourceId) = SourceQuotaExhaustedException(id)

    private fun composite(
        vararg providers: FakeProvider,
        searchOrder: List<SourceId> = emptyList(),
        isbnOrder: List<SourceId> = emptyList(),
        isbnOrderByGroup: Map<IsbnGroup, List<SourceId>> = emptyMap(),
    ) = CompositeBookMetadataSource(providers.toList(), searchOrder, IsbnOrder(isbnOrder, isbnOrderByGroup))

    // Order and capabilities

    /** E1: the configured order decides whose hits come first, not the bean order. */
    @Test
    fun `lists hits in the configured search order`() {
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Eragon (library)")))
        val finder = FakeProvider(FINDER, hits = listOf(hit(FINDER, "Eragon (finder)")))

        val titles = composite(library, finder, searchOrder = listOf(FINDER, LIBRARY)).search("eragon").map { it.title }

        assertEquals(listOf("Eragon (finder)", "Eragon (library)"), titles)
    }

    @Test
    fun `puts providers missing from the order after the listed ones, by id`() {
        val archive = FakeProvider(ARCHIVE, hits = listOf(hit(ARCHIVE, "a")))
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "l")))
        val finder = FakeProvider(FINDER, hits = listOf(hit(FINDER, "f")))

        val titles = composite(library, finder, archive, searchOrder = listOf(LIBRARY, SourceId.of("gone")))
            .search("x").map { it.title }

        assertEquals(listOf("l", "a", "f"), titles)
    }

    /** E6: a provider is only asked what its descriptor says it can answer. */
    @Test
    fun `never asks a search-only provider for an ISBN, nor a lookup-only one to search`() {
        val finder = FakeProvider(FINDER, isbn = false)
        val library = FakeProvider(LIBRARY, search = false)
        val composite = composite(finder, library)

        composite.findByIsbn(ISBN)
        composite.search("eragon")

        assertEquals(listOf("search"), finder.calls)
        assertEquals(listOf("isbn"), library.calls)
    }

    @Test
    fun `derives its own capabilities from the providers and never claims to be storable`() {
        val descriptor = composite(FakeProvider(FINDER, isbn = false)).descriptor

        assertTrue(descriptor.supportsFreeTextSearch)
        assertEquals(false, descriptor.supportsIsbnLookup)
        assertEquals(false, descriptor.persistableInPublicCatalog)
    }

    @Test
    fun `refuses two providers with the same id`() {
        assertFailsWith<IllegalArgumentException> { composite(FakeProvider(FINDER), FakeProvider(FINDER)) }
    }

    // Search outages (9.7 D4)

    @Test
    fun `answers from the others when one provider fails`() {
        val finder = FakeProvider(FINDER, failure = down(FINDER))
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Eragon")))

        assertEquals(listOf("Eragon"), composite(finder, library).search("eragon").map { it.title })
    }

    /** E8d: nothing found, but a provider failed — it might have had the book. */
    @Test
    fun `reports an outage when nothing was found and a provider failed`() {
        val finder = FakeProvider(FINDER, failure = down(FINDER))
        val library = FakeProvider(LIBRARY)

        val e = assertFailsWith<SourceUnavailableException> { composite(finder, library).search("eragon") }
        assertEquals(FINDER, e.source)
    }

    /** E8e: a provider with a spent quota was not asked; the clean miss of the others is the answer. */
    @Test
    fun `does not count a spent quota as failed`() {
        val finder = FakeProvider(FINDER, failure = quotaSpent(FINDER))
        val library = FakeProvider(LIBRARY)

        assertEquals(emptyList(), composite(finder, library).search("eragon"))
    }

    /** D11: when every provider was skipped, nobody looked — that is not "no such book". */
    @Test
    fun `reports an outage when every provider was skipped for a spent quota`() {
        val composite = composite(FakeProvider(FINDER, failure = quotaSpent(FINDER)))

        assertFailsWith<SourceQuotaExhaustedException> { composite.search("eragon") }
    }

    @Test
    fun `reports the failure when one provider failed and the other was skipped`() {
        val finder = FakeProvider(FINDER, failure = quotaSpent(FINDER))
        val library = FakeProvider(LIBRARY, failure = down(LIBRARY))

        val e = assertFailsWith<SourceUnavailableException> { composite(finder, library).search("eragon") }
        assertEquals(LIBRARY, e.source)
        assertEquals(1, e.suppressed.size)
    }

    @Test
    fun `still returns hits when the others were skipped`() {
        val finder = FakeProvider(FINDER, failure = quotaSpent(FINDER))
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Eragon")))

        assertEquals(listOf("Eragon"), composite(finder, library).search("eragon").map { it.title })
    }

    /** The catalog-only setup: no source to ask, so a miss is a miss (warned about at startup). */
    @Test
    fun `is a miss when no provider is running at all`() {
        val composite = composite()

        assertEquals(emptyList(), composite.search("eragon"))
        assertNull(composite.findByIsbn(ISBN))
    }

    @Test
    fun `answers with an empty list when every provider found nothing`() {
        assertEquals(emptyList(), composite(FakeProvider(FINDER), FakeProvider(LIBRARY)).search("zzzz"))
    }

    @Test
    fun `keeps every failure when all providers fail`() {
        val e = assertFailsWith<SourceUnavailableException> {
            composite(FakeProvider(FINDER, failure = down(FINDER)), FakeProvider(LIBRARY, failure = down(LIBRARY))).search("x")
        }

        assertEquals(1, e.suppressed.size)
    }

    // Interrupts (code review): a provider asked on an interrupted thread fails at once, and its
    // circuit breaker would count a healthy source as failing

    /** The first provider is interrupted during its retry pause, as GoogleBooksClient does. */
    private fun interruptedProvider(id: SourceId) = object : BookMetadataSource by FakeProvider(id) {
        override fun search(text: String): List<BookCandidate> {
            Thread.currentThread().interrupt()
            throw down(id)
        }

        override fun findByIsbn(isbn: Isbn): BookCandidate? {
            Thread.currentThread().interrupt()
            throw down(id)
        }
    }

    private fun <T> onInterruptibleThread(block: () -> T): T =
        try {
            block()
        } finally {
            Thread.interrupted()
        }

    @Test
    fun `asks no further provider once the request is interrupted`() {
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Eragon")))
        val composite = CompositeBookMetadataSource(listOf(interruptedProvider(FINDER), library), listOf(FINDER, LIBRARY), IsbnOrder(emptyList()))

        onInterruptibleThread {
            val e = assertFailsWith<SourceUnavailableException> { composite.search("eragon") }

            assertEquals(FINDER, e.source)
            assertTrue(Thread.currentThread().isInterrupted)
        }
        assertTrue(library.calls.isEmpty())
    }

    @Test
    fun `keeps the hits found before the interrupt`() {
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Eragon")))
        val composite = CompositeBookMetadataSource(listOf(library, interruptedProvider(FINDER), FakeProvider(ARCHIVE)), listOf(LIBRARY, FINDER, ARCHIVE), IsbnOrder(emptyList()))

        val titles = onInterruptibleThread { composite.search("eragon").map { it.title } }

        assertEquals(listOf("Eragon"), titles)
    }

    @Test
    fun `asks no further ISBN provider once the request is interrupted`() {
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Eragon"))
        val composite = CompositeBookMetadataSource(listOf(interruptedProvider(LIBRARY), archive), emptyList(), IsbnOrder(listOf(LIBRARY, ARCHIVE)))

        onInterruptibleThread { assertFailsWith<SourceUnavailableException> { composite.findByIsbn(ISBN) } }
        assertTrue(archive.calls.isEmpty())
    }

    /** A bug in a provider is not an outage and stays a 500. */
    @Test
    fun `does not swallow an unexpected exception`() {
        assertFailsWith<IllegalStateException> {
            composite(FakeProvider(FINDER, failure = IllegalStateException("bug")), FakeProvider(LIBRARY)).search("x")
        }
    }

    // ISBN lookup (9.7 D5)

    @Test
    fun `takes the first provider that knows the ISBN and asks no further`() {
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Eragon"))
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Eragon (archive)"))

        val found = composite(archive, library, isbnOrder = listOf(LIBRARY, ARCHIVE)).findByIsbn(ISBN)

        assertEquals("Eragon", found?.title)
        assertTrue(archive.calls.isEmpty())
    }

    @Test
    fun `finds the ISBN at the next provider when the first one fails`() {
        val library = FakeProvider(LIBRARY, failure = down(LIBRARY))
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Eragon"))

        assertEquals("Eragon", composite(library, archive, isbnOrder = listOf(LIBRARY, ARCHIVE)).findByIsbn(ISBN)?.title)
    }

    /** D5: a scan must not send the user to the manual form for a book a failing source might know. */
    @Test
    fun `reports an outage when no provider knows the ISBN and one failed`() {
        val library = FakeProvider(LIBRARY, failure = down(LIBRARY))
        val archive = FakeProvider(ARCHIVE)

        assertFailsWith<SourceUnavailableException> { composite(library, archive).findByIsbn(ISBN) }
    }

    @Test
    fun `reports an outage when every ISBN provider was skipped for a spent quota`() {
        assertFailsWith<SourceQuotaExhaustedException> {
            composite(FakeProvider(LIBRARY, failure = quotaSpent(LIBRARY))).findByIsbn(ISBN)
        }
    }

    @Test
    fun `is a miss when one ISBN provider was skipped and another answered cleanly`() {
        val library = FakeProvider(LIBRARY, failure = quotaSpent(LIBRARY))
        val archive = FakeProvider(ARCHIVE)

        assertNull(composite(library, archive).findByIsbn(ISBN))
    }

    @Test
    fun `is a miss when every provider answered cleanly`() {
        assertNull(composite(FakeProvider(LIBRARY), FakeProvider(ARCHIVE)).findByIsbn(ISBN))
    }

    // ISBN order by group (9.8 Q1, Q5)

    private val germanLanguageIsbn = ISBN // 978-3…
    private val englishIsbn = Isbn.of("9780735619678")

    private fun keyed(vararg providers: FakeProvider) = composite(
        *providers,
        isbnOrder = listOf(LIBRARY, ARCHIVE),
        isbnOrderByGroup = mapOf(IsbnGroup.GERMAN_LANGUAGE to listOf(ARCHIVE, LIBRARY)),
    )

    /** E12: the group's own order — the second provider is not asked once the first knows the ISBN. */
    @Test
    fun `asks in the group's order for an ISBN of that group`() {
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Eragon (library)"))
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Eragon (archive)"))

        assertEquals("Eragon (archive)", keyed(library, archive).findByIsbn(germanLanguageIsbn)?.title)
        assertTrue(library.calls.isEmpty())
    }

    /** E11, E15: an ISBN in no group uses the default order. */
    @Test
    fun `asks in the default order for an ISBN in no group`() {
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Code Complete (library)"))
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Code Complete (archive)"))

        assertEquals("Code Complete (library)", keyed(library, archive).findByIsbn(englishIsbn)?.title)
        assertTrue(archive.calls.isEmpty())
    }

    /** E14: the provider listed last is the fallback, asked only after the first one missed. */
    @Test
    fun `asks the provider listed last when the first one misses`() {
        val library = FakeProvider(LIBRARY)
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Cik"))

        assertEquals("Cik", keyed(library, archive).findByIsbn(englishIsbn)?.title)
        assertEquals(listOf("isbn"), library.calls)
    }

    @Test
    fun `uses the default order for a group without its own`() {
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Eragon (library)"))
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Eragon (archive)"))

        val found = composite(library, archive, isbnOrder = listOf(LIBRARY, ARCHIVE)).findByIsbn(germanLanguageIsbn)

        assertEquals("Eragon (library)", found?.title)
    }

    @Test
    fun `the order differs per group, the providers do not`() {
        val composite = keyed(FakeProvider(LIBRARY), FakeProvider(ARCHIVE), FakeProvider(FINDER, isbn = false))

        assertEquals(listOf(LIBRARY, ARCHIVE), composite.isbnProviders.map { it.descriptor.id })
        assertEquals(
            listOf(ARCHIVE, LIBRARY),
            composite.isbnProvidersByGroup.getValue(IsbnGroup.GERMAN_LANGUAGE).map { it.descriptor.id },
        )
    }

    // Configuration

    /** A disabled provider may stay listed; a misspelled one is only found by this warning. */
    @Test
    fun `names order ids that match no running provider`() {
        val unmatched = CompositeBookMetadataSource.unmatchedOrderIds(
            order = listOf(FINDER, SourceId.of("findr"), LIBRARY),
            sources = listOf(FakeProvider(FINDER), FakeProvider(LIBRARY)),
        )

        assertEquals(listOf(SourceId.of("findr")), unmatched)
    }

    // Records

    @Test
    fun `asks only the provider that issued a record`() {
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Eragon")))
        val archive = FakeProvider(ARCHIVE)

        val found = composite(library, archive).findByRecordId(SourceRef(LIBRARY, SourceRecordId("Eragon")))

        assertEquals("Eragon", found?.title)
        assertTrue(archive.calls.isEmpty())
    }

    /** A disabled provider's hit ids lead nowhere: the add is a 404, not an error. */
    @Test
    fun `is a miss for a record of a provider that is not running`() {
        assertNull(composite(FakeProvider(LIBRARY)).findByRecordId(SourceRef(FINDER, SourceRecordId("x"))))
    }

    @Test
    fun `a provider failing to fetch a record fails the fetch`() {
        val failing = object : BookMetadataSource by FakeProvider(LIBRARY) {
            override fun findByRecordId(ref: SourceRef): BookCandidate? = throw down(LIBRARY)
        }
        val composite = CompositeBookMetadataSource(listOf(failing), emptyList(), IsbnOrder(emptyList()))

        val e = assertFailsWith<SourceUnavailableException> { composite.findByRecordId(SourceRef(LIBRARY, SourceRecordId("x"))) }
        assertEquals(LIBRARY, e.source)
    }
}
