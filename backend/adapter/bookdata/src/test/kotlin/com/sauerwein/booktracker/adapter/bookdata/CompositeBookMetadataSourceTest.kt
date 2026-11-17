package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceQuotaExhaustedException
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.addRef
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ISBN = Isbn.of("9783570402931")
private val OTHER_ISBN = Isbn.of("9780735619678")
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
        persistable: Boolean = true,
    ) : BookMetadataSource {
        /** Written on the provider's own thread, read after the composite returned. */
        val calls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

        override val descriptor = SourceDescriptor(
            id = id,
            supportsIsbnLookup = isbn,
            supportsFreeTextSearch = search,
            providesCovers = false,
            persistableInPublicCatalog = persistable,
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

    private fun hit(
        source: SourceId,
        title: String,
        isbn: Isbn? = null,
        publisher: String? = null,
        coverUrl: String? = null,
        persistable: Boolean = true,
        language: Language? = null,
        authors: List<String> = listOf("Christopher Paolini"),
    ) = BookCandidate(
        title = title,
        authors = authors.map { Author(it, ref = null) },
        publisher = publisher,
        isbn = isbn,
        publicationYear = null,
        pageCount = null,
        coverUrl = coverUrl,
        language = language,
        source = source,
        sourceId = SourceRecordId(title),
        workKey = null,
        persistableInPublicCatalog = persistable,
    )

    private fun down(id: SourceId) = SourceUnavailableException(id)
    private fun quotaSpent(id: SourceId) = SourceQuotaExhaustedException(id)


    /** Fields in the order the test lists the providers, the same for every language. */
    private fun precedence(order: List<SourceId>) = MergePrecedence(default = FieldPrecedence(fields = order))

    private fun composite(
        vararg providers: BookMetadataSource,
        searchOrder: List<SourceId> = emptyList(),
        precedence: MergePrecedence = precedence(listOf(LIBRARY, ARCHIVE, FINDER)),
    ) = CompositeBookMetadataSource(providers.toList(), searchOrder, BookCandidateMerge(precedence))

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

    // Parallel fan-out (9.9)

    /**
     * A6: the first provider only answers once the second one has been asked. Asked one after
     * another, the first would wait for nothing and give up — so this passes only in parallel.
     */
    private fun pairWaitingOnEachOther(): Pair<BookMetadataSource, BookMetadataSource> {
        val secondAsked = CountDownLatch(1)
        val first = object : BookMetadataSource by FakeProvider(LIBRARY) {
            private fun waitForSecond(): Boolean = secondAsked.await(5, TimeUnit.SECONDS)
            override fun search(text: String) =
                if (waitForSecond()) listOf(hit(LIBRARY, "first")) else error("asked one after another")
            override fun findByIsbn(isbn: Isbn) =
                if (waitForSecond()) hit(LIBRARY, "first", isbn = isbn) else error("asked one after another")
        }
        val second = object : BookMetadataSource by FakeProvider(ARCHIVE) {
            override fun search(text: String): List<BookCandidate> {
                secondAsked.countDown()
                return listOf(hit(ARCHIVE, "second"))
            }
            override fun findByIsbn(isbn: Isbn): BookCandidate? {
                secondAsked.countDown()
                return hit(ARCHIVE, "second", isbn = isbn)
            }
        }
        return first to second
    }

    @Test
    fun `asks the search providers in parallel`() {
        val (first, second) = pairWaitingOnEachOther()

        val titles = composite(first, second, searchOrder = listOf(LIBRARY, ARCHIVE)).search("eragon").map { it.title }

        assertEquals(listOf("first", "second"), titles)
    }

    @Test
    fun `asks the ISBN providers in parallel`() {
        val (first, second) = pairWaitingOnEachOther()

        assertEquals("first", composite(first, second).findByIsbn(ISBN)?.title)
    }

    // Merge (9.9)

    /** A4: one edition from two providers is one hit, with what each of them knows. */
    @Test
    fun `merges search hits with the same ISBN into one, at the position of the first`() {
        val finder = FakeProvider(
            FINDER,
            hits = listOf(hit(FINDER, "Eragon (finder)", isbn = ISBN, coverUrl = "https://finder/c.jpg"), hit(FINDER, "Eldest", isbn = OTHER_ISBN)),
        )
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Eragon", isbn = ISBN, publisher = "cbj")))

        val hits = composite(finder, library, searchOrder = listOf(FINDER, LIBRARY)).search("eragon")

        assertEquals(listOf("Eragon", "Eldest"), hits.map { it.title })
        val eragon = hits.first()
        assertEquals(LIBRARY, eragon.source)
        assertEquals("cbj", eragon.publisher)
        assertEquals("https://finder/c.jpg", eragon.coverUrl)
    }

    /**
     * A11: a search hit without an author is listed like any other, and takes the authors of a hit
     * with the same ISBN when another provider has one.
     */
    @Test
    fun `lists a search hit without an author, merged with another provider's authors where they share an ISBN`() {
        val finder = FakeProvider(
            FINDER, isbn = false,
            hits = listOf(hit(FINDER, "Duden", isbn = ISBN, authors = emptyList()), hit(FINDER, "Tschick", isbn = OTHER_ISBN, authors = emptyList())),
        )
        val archive = FakeProvider(ARCHIVE, isbn = false, hits = listOf(hit(ARCHIVE, "Tschick (archive)", isbn = OTHER_ISBN, authors = listOf("Wolfgang Herrndorf"))))

        val hits = composite(finder, archive, searchOrder = listOf(FINDER, ARCHIVE)).search("x")

        assertEquals(listOf(emptyList(), listOf("Wolfgang Herrndorf")), hits.map { hit -> hit.authors.map { it.name } })
    }

    /** Q5: without an ISBN, two hits are never assumed to be one edition. */
    @Test
    fun `never merges search hits without an ISBN`() {
        val finder = FakeProvider(FINDER, hits = listOf(hit(FINDER, "Tschick")))
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Tschick")))

        assertEquals(2, composite(finder, library).search("tschick").size)
    }

    /** I12: a hit merged with data that may not be stored cannot be stored either. */
    @Test
    fun `a hit merged with data that may not be stored is not storable`() {
        val finder = FakeProvider(FINDER, hits = listOf(hit(FINDER, "Eragon", isbn = ISBN, coverUrl = "https://finder/c.jpg", persistable = false)))
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Eragon", isbn = ISBN)))

        val merged = composite(finder, library).search("eragon").single()

        assertEquals(false, merged.persistableInPublicCatalog)
        // I15: so it is added by its ISBN alone, with no record of its own to fall back to
        assertEquals(CatalogBookRef.ByIsbn(ISBN), merged.addRef)
    }

    /** Two hits of one provider with one ISBN are one edition too (two Google volumes of a printing). */
    @Test
    fun `merges two hits of the same provider with the same ISBN`() {
        val finder = FakeProvider(
            FINDER,
            hits = listOf(hit(FINDER, "Eragon (scan)", isbn = ISBN), hit(FINDER, "Eragon (publisher)", isbn = ISBN, publisher = "cbj")),
        )

        val merged = composite(finder).search("eragon").single()

        assertEquals("Eragon (scan)", merged.title)
        assertEquals("cbj", merged.publisher)
    }

    /** The merged hit's language picks the field order (9.9 Q3), also through the composite. */
    @Test
    fun `the merged record's language picks the field order`() {
        val german = Language.of("de")
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Der kleine Hobbit (library)", isbn = ISBN))
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Der kleine Hobbit (archive)", isbn = ISBN, language = german))
        val precedence = MergePrecedence(
            default = FieldPrecedence(fields = listOf(LIBRARY, ARCHIVE)),
            byLanguage = mapOf(german to FieldPrecedence(fields = listOf(ARCHIVE, LIBRARY))),
        )

        val found = composite(library, archive, precedence = precedence).findByIsbn(ISBN)

        assertEquals("Der kleine Hobbit (archive)", found?.title)
        assertEquals(german, found?.language)
    }

    /** Q1, A1: a scan asks everyone and stores what all of them know about the edition. */
    @Test
    fun `merges what every ISBN provider knows, even after the first one found it`() {
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Eragon", isbn = ISBN, publisher = "cbj"))
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Eragon (archive)", isbn = ISBN, coverUrl = "https://archive/c.jpg"))

        val found = composite(library, archive).findByIsbn(ISBN)

        assertEquals("Eragon", found?.title)
        assertEquals(LIBRARY, found?.source)
        assertEquals("cbj", found?.publisher)
        assertEquals("https://archive/c.jpg", found?.coverUrl)
        assertEquals(listOf("isbn"), archive.calls)
    }

    /** A2: the configured precedence, not the provider list, decides whose title and identity win. */
    @Test
    fun `the precedence decides whose record names the merged one`() {
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Eragon (library)", isbn = ISBN))
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Eragon (archive)", isbn = ISBN))

        val found = composite(library, archive, precedence = precedence(listOf(ARCHIVE, LIBRARY))).findByIsbn(ISBN)

        assertEquals("Eragon (archive)", found?.title)
        assertEquals(ARCHIVE, found?.source)
    }

    /** 9.10 Q9, A10: the Duden has no author anywhere — still a found book, not a miss. */
    @Test
    fun `a book without an author is found`() {
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Duden", isbn = ISBN, authors = emptyList()))
        val archive = FakeProvider(ARCHIVE)

        val found = composite(library, archive).findByIsbn(ISBN)

        assertEquals("Duden", found?.title)
        assertEquals(emptyList(), found?.authors)
    }

    /** A9: a record without an author still fills the gaps of one with an author. */
    @Test
    fun `a record without an author contributes its other fields`() {
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Harry Potter", isbn = ISBN))
        val archive = FakeProvider(
            ARCHIVE, isbnHit = hit(ARCHIVE, "Harry Potter (archive)", isbn = ISBN, coverUrl = "https://archive/c.jpg", authors = emptyList()),
        )

        val found = composite(library, archive).findByIsbn(ISBN)

        assertEquals(listOf("Christopher Paolini"), found?.authors?.map { it.name })
        assertEquals("https://archive/c.jpg", found?.coverUrl)
    }

    /** The scanned ISBN is the row's ISBN, whichever form a provider reports. */
    @Test
    fun `the merged record carries the ISBN that was asked`() {
        val library = FakeProvider(LIBRARY, isbnHit = hit(LIBRARY, "Eragon", isbn = OTHER_ISBN))

        assertEquals(ISBN, composite(library).findByIsbn(ISBN)?.isbn)
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

    /** A bug in a provider is not an outage and stays a 500. */
    @Test
    fun `does not swallow an unexpected exception`() {
        assertFailsWith<IllegalStateException> {
            composite(FakeProvider(FINDER, failure = IllegalStateException("bug")), FakeProvider(LIBRARY)).search("x")
        }
        assertFailsWith<IllegalStateException> {
            composite(FakeProvider(LIBRARY, failure = IllegalStateException("bug")), FakeProvider(ARCHIVE)).findByIsbn(ISBN)
        }
    }

    // ISBN lookup outages (9.7 D5)

    /** A3: a provider that fails only shortens the record; the others still answer. */
    @Test
    fun `finds the ISBN at another provider when one fails`() {
        val library = FakeProvider(LIBRARY, failure = down(LIBRARY))
        val archive = FakeProvider(ARCHIVE, isbnHit = hit(ARCHIVE, "Eragon", isbn = ISBN))

        assertEquals("Eragon", composite(library, archive).findByIsbn(ISBN)?.title)
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

    @Test
    fun `asks every ISBN provider, whatever their order`() {
        val composite = composite(FakeProvider(LIBRARY), FakeProvider(ARCHIVE), FakeProvider(FINDER, isbn = false))

        assertEquals(listOf(ARCHIVE, LIBRARY), composite.isbnProviders.map { it.descriptor.id })
    }

    // Interrupts (code review): once the request is interrupted, the composite stops waiting

    private fun <T> onInterruptibleThread(block: () -> T): T =
        try {
            block()
        } finally {
            Thread.interrupted()
        }

    @Test
    fun `asks no provider once the request is interrupted`() {
        val library = FakeProvider(LIBRARY, hits = listOf(hit(LIBRARY, "Eragon")), isbnHit = hit(LIBRARY, "Eragon"))
        val composite = composite(library)

        onInterruptibleThread {
            Thread.currentThread().interrupt()
            val e = assertFailsWith<SourceUnavailableException> { composite.search("eragon") }
            assertEquals(LIBRARY, e.source)
            assertFailsWith<SourceUnavailableException> { composite.findByIsbn(ISBN) }
            assertTrue(Thread.currentThread().isInterrupted)
        }
        assertTrue(library.calls.isEmpty())
    }

    /**
     * A fast provider that has answered and a slow one that interrupts the request thread once the
     * request is parked waiting for it — so the fast answer is in, and the interrupt is what ends
     * the wait. [release] lets the slow provider finish afterwards.
     */
    private inner class InterruptWhileWaiting(private val requestThread: Thread) {
        val release = CountDownLatch(1)
        private val fastDone = CountDownLatch(1)

        val fast = object : BookMetadataSource by FakeProvider(LIBRARY) {
            override fun search(text: String) = listOf(hit(LIBRARY, "Eragon")).also { fastDone.countDown() }
            override fun findByIsbn(isbn: Isbn) = hit(LIBRARY, "Eragon", isbn = isbn).also { fastDone.countDown() }
        }

        val slow = object : BookMetadataSource by FakeProvider(ARCHIVE) {
            override fun search(text: String): List<BookCandidate> = interruptThenWait().let { listOf(hit(ARCHIVE, "too late")) }
            override fun findByIsbn(isbn: Isbn): BookCandidate = interruptThenWait().let { hit(ARCHIVE, "too late", isbn = isbn) }
        }

        private fun interruptThenWait() {
            fastDone.await(5, TimeUnit.SECONDS)
            // Parked in `get()`: either already on the slow answer, or about to take the fast one, which is complete
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (requestThread.state != Thread.State.WAITING && System.nanoTime() < deadline) Thread.onSpinWait()
            Thread.sleep(50)
            requestThread.interrupt()
            release.await(5, TimeUnit.SECONDS)
        }
    }

    /**
     * Interrupted while waiting for a slow provider: what the fast one found is still the answer,
     * the slow one counts as failed, and it is left running rather than interrupted.
     */
    @Test
    fun `keeps the search hits found before the interrupt and stops waiting for the rest`() {
        val setup = InterruptWhileWaiting(Thread.currentThread())

        try {
            val titles = onInterruptibleThread {
                composite(setup.fast, setup.slow, searchOrder = listOf(LIBRARY, ARCHIVE)).search("eragon").map { it.title }
            }
            assertEquals(listOf("Eragon"), titles)
        } finally {
            setup.release.countDown()
        }
    }

    /** Review F4: a partial ISBN merge would be stored for good — an interrupted lookup is an outage instead. */
    @Test
    fun `an interrupted ISBN lookup is an outage, not the partial merge`() {
        val setup = InterruptWhileWaiting(Thread.currentThread())

        try {
            onInterruptibleThread {
                val e = assertFailsWith<SourceUnavailableException> { composite(setup.fast, setup.slow).findByIsbn(ISBN) }
                assertEquals(ARCHIVE, e.source)
                assertTrue(Thread.currentThread().isInterrupted)
            }
        } finally {
            setup.release.countDown()
        }
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

        val e = assertFailsWith<SourceUnavailableException> { composite(failing).findByRecordId(SourceRef(LIBRARY, SourceRecordId("x"))) }
        assertEquals(LIBRARY, e.source)
    }
}
