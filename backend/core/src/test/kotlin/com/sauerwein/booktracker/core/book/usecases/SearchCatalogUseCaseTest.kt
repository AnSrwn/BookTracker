package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.FakeBookMetadataSource
import com.sauerwein.booktracker.core.book.exception.SourceQuotaExhaustedException
import com.sauerwein.booktracker.core.book.exception.SourceUnavailableException
import com.sauerwein.booktracker.core.book.InMemoryBookRepository
import com.sauerwein.booktracker.core.book.models.BookKind
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.testBook
import com.sauerwein.booktracker.core.book.testCandidate
import com.sauerwein.booktracker.core.user.UserId
import java.time.Clock
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MIN_LOCAL_HITS = 2
private val TEST_SOURCE = SourceId.of("test_source")
private val ERAGON_US = Isbn.of("9780375826689")
private val ERAGON_DE = Isbn.of("9783570402931")

class SearchCatalogUseCaseTest {

    private val user = UserId(UUID.randomUUID())
    private val otherUser = UserId(UUID.randomUUID())
    private val bookRepository = InMemoryBookRepository()

    private val storeCatalogEntry = StoreCatalogEntryUseCase(bookRepository, Clock.systemUTC())

    private fun useCase(source: FakeBookMetadataSource) = SearchCatalogUseCase(
        resolveIsbnUseCase = ResolveIsbnUseCase(bookRepository, source, storeCatalogEntry),
        bookRepository = bookRepository,
        bookMetadataSource = source,
        currentUserProvider = { user },
        minLocalHits = MIN_LOCAL_HITS,
    )

    @Test
    fun `runs a text search for ordinary words`() {
        val hits = listOf(
            testCandidate(title = "Eragon"),
            testCandidate(title = "Eldest", sourceId = SourceRecordId("test-record-2"))
        )
        val source = FakeBookMetadataSource(searchResults = hits)

        assertEquals(hits, useCase(source)("eragon"))
        assertEquals(listOf("eragon"), source.searchedTexts)
        assertEquals(emptyList(), source.lookedUpIsbns)
    }

    /** A user pasting an ISBN into the search box must get the exact edition, not a word search. */
    @Test
    fun `routes an ISBN in the search box to the exact lookup`() {
        val candidate = testCandidate(title = "Eragon", isbn = Isbn.of("9783570402931"))
        val source = FakeBookMetadataSource(isbnResult = candidate)

        assertEquals(listOf("Eragon"), useCase(source)("978-3-570-40293-1").map { it.title })
        assertEquals(listOf(Isbn.of("9783570402931")), source.lookedUpIsbns)
        assertEquals(emptyList(), source.searchedTexts)
    }

    @Test
    fun `an ISBN in the search box fills the catalog like a scan does`() {
        val source = FakeBookMetadataSource(isbnResult = testCandidate(isbn = Isbn.of("9783570402931")))

        useCase(source)("9783570402931")

        assertEquals(1, bookRepository.stored().size)
    }

    /**
     * The asymmetry to FindBookByIsbnUseCase: at the search endpoint an unknown ISBN is an
     * empty result, while the scan endpoint throws so the app can open the manual form.
     */
    @Test
    fun `returns an empty list for an unknown ISBN instead of throwing`() {
        val source = FakeBookMetadataSource(isbnResult = null)

        assertEquals(emptyList(), useCase(source)("9783570402931"))
    }

    @Test
    fun `returns an empty list when a text search finds nothing`() {
        val source = FakeBookMetadataSource(searchResults = emptyList())

        assertEquals(emptyList(), useCase(source)("nonexistent title"))
    }

    @Test
    fun `answers from the catalog alone once it has enough hits`() {
        bookRepository.seed(testBook(ownerId = null, title = "Eragon"))
        bookRepository.seed(testBook(ownerId = user, title = "Eragon, signed"))
        val source = FakeBookMetadataSource(searchResults = listOf(testCandidate(title = "Eragon")))

        val titles = useCase(source)("eragon").map { it.title }

        assertEquals(listOf("Eragon", "Eragon, signed"), titles)
        assertEquals(emptyList(), source.searchedTexts)
    }

    @Test
    fun `puts local hits first and drops source hits for the same record`() {
        val source = SourceId.of("open_library")
        bookRepository.seed(
            testBook(
                ownerId = null,
                title = "Eragon",
                source = source,
                sourceId = SourceRecordId("OL1M")
            )
        )
        val metadataSource = FakeBookMetadataSource(
            searchResults = listOf(
                testCandidate(title = "Eragon (remote copy)", source = source, sourceId = SourceRecordId("OL1M")),
                testCandidate(title = "Eragon, other edition", source = source, sourceId = SourceRecordId("OL2M")),
                testCandidate(title = "Eragon, no id", sourceId = null),
            ),
        )

        val titles = useCase(metadataSource)("eragon").map { it.title }

        assertEquals(listOf("Eragon", "Eragon, other edition", "Eragon, no id"), titles)
    }

    @Test
    fun `matches every word across title and authors`() {
        bookRepository.seed(testBook(ownerId = null, title = "Eragon", author = "Christopher Paolini"))
        val source = FakeBookMetadataSource()

        assertEquals(listOf("Eragon"), useCase(source)("eragon paolini").map { it.title })
    }

    @Test
    fun `never returns another user's private book`() {
        bookRepository.seed(testBook(ownerId = otherUser, title = "Eragon"))
        bookRepository.seed(testBook(ownerId = otherUser, title = "Eragon II"))
        val source = FakeBookMetadataSource()

        assertTrue(useCase(source)("eragon").isEmpty())
    }

    /** D27: search only reads. A hit enters the catalog when a user adds it, from the full record. */
    @Test
    fun `never stores a source hit, not even one with an ISBN`() {
        val hit = testCandidate(title = "Eragon", isbn = Isbn.of("9780375826689"))
        val source = FakeBookMetadataSource(searchResults = listOf(hit))

        assertEquals(listOf(hit), useCase(source)("eragon"))
        assertTrue(bookRepository.stored().isEmpty())
    }

    @Test
    fun `leaves source hits without a stored id`() {
        val source = FakeBookMetadataSource(searchResults = listOf(testCandidate(title = "Eragon", isbn = Isbn.of("9780375826689"))))

        val hit = useCase(source)("eragon").single()

        assertNull(hit.bookId)
        assertNull(hit.kind)
    }

    @Test
    fun `local hits carry their id and kind`() {
        val catalogBook = testBook(ownerId = null, title = "Eragon")
        val ownBook = testBook(ownerId = user, title = "Eragon, signed")
        bookRepository.seed(catalogBook, ownBook)

        val hits = useCase(FakeBookMetadataSource())("eragon").associateBy { it.title }

        assertEquals(catalogBook.id, hits.getValue("Eragon").bookId)
        assertEquals(BookKind.CATALOG, hits.getValue("Eragon").kind)
        assertEquals(ownBook.id, hits.getValue("Eragon, signed").bookId)
        assertEquals(BookKind.PRIVATE, hits.getValue("Eragon, signed").kind)
    }

    // Outages (9.7 D2, D4)

    /** D2: with local hits a failing source only shortens the answer. */
    @Test
    fun `returns the local hits when the sources fail`() {
        bookRepository.seed(testBook(ownerId = null, title = "Eragon"))
        val source = FakeBookMetadataSource(searchFailure = SourceUnavailableException(TEST_SOURCE))

        assertEquals(listOf("Eragon"), useCase(source)("eragon").map { it.title })
    }

    /** D4: without local hits an outage is the answer, never an empty list that reads as "no such book". */
    @Test
    fun `fails when the sources fail and there is nothing local`() {
        val source = FakeBookMetadataSource(searchFailure = SourceUnavailableException(TEST_SOURCE))

        assertFailsWith<SourceUnavailableException> { useCase(source)("eragon") }
    }

    @Test
    fun `a spent quota alone fails the same way`() {
        val source = FakeBookMetadataSource(searchFailure = SourceQuotaExhaustedException(TEST_SOURCE))

        assertFailsWith<SourceUnavailableException> { useCase(source)("eragon") }
    }

    // Hits whose data may not be stored (9.7 D6, D8)

    @Test
    fun `drops a hit that may not be stored and has no ISBN`() {
        val source = FakeBookMetadataSource(
            searchResults = listOf(
                testCandidate(title = "Eragon, no ISBN", persistableInPublicCatalog = false),
                testCandidate(title = "Eragon", isbn = ERAGON_US, persistableInPublicCatalog = false),
            ),
        )

        assertEquals(listOf("Eragon"), useCase(source)("eragon").map { it.title })
    }

    /** A CC0 hit without an ISBN stays: it can still be copied into a private book. */
    @Test
    fun `keeps a storable hit without an ISBN`() {
        val source = FakeBookMetadataSource(searchResults = listOf(testCandidate(title = "Eragon")))

        assertEquals(listOf("Eragon"), useCase(source)("eragon").map { it.title })
    }

    // ISBN dedupe (9.7 D10, A4)

    /** E7e: the first hit with an ISBN wins as it is; merging fields is 9.9. */
    @Test
    fun `keeps only the first hit per ISBN`() {
        val source = FakeBookMetadataSource(
            searchResults = listOf(
                testCandidate(title = "Eragon (first)", isbn = ERAGON_US, sourceId = SourceRecordId("a")),
                testCandidate(title = "Eragon (second)", isbn = ERAGON_US, sourceId = SourceRecordId("b")),
                testCandidate(title = "Eragon, German", isbn = ERAGON_DE, sourceId = SourceRecordId("c")),
            ),
        )

        assertEquals(listOf("Eragon (first)", "Eragon, German"), useCase(source)("eragon").map { it.title })
    }

    /** E7f: a stored catalog book beats any hit for its ISBN. */
    @Test
    fun `drops a hit whose ISBN is already a local catalog book`() {
        bookRepository.seed(testBook(ownerId = null, title = "Eragon", isbn = ERAGON_US))
        val source = FakeBookMetadataSource(
            searchResults = listOf(testCandidate(title = "Eragon (hit)", isbn = ERAGON_US, persistableInPublicCatalog = false)),
        )

        assertEquals(listOf("Eragon"), useCase(source)("eragon").map { it.title })
    }

    /** E7g / A4: a private copy does not hide the catalog book for the same ISBN (9.6 D12). */
    @Test
    fun `a private book with the ISBN does not hide the hit`() {
        bookRepository.seed(testBook(ownerId = user, title = "Eragon, my copy", isbn = ERAGON_US))
        val source = FakeBookMetadataSource(
            searchResults = listOf(testCandidate(title = "Eragon", isbn = ERAGON_US)),
        )

        assertEquals(listOf("Eragon, my copy", "Eragon"), useCase(source)("eragon").map { it.title })
    }

    @Test
    fun `hits without an ISBN are never deduplicated`() {
        val source = FakeBookMetadataSource(
            searchResults = listOf(
                testCandidate(title = "Eragon", sourceId = SourceRecordId("a")),
                testCandidate(title = "Eragon", sourceId = SourceRecordId("b")),
            ),
        )

        assertEquals(2, useCase(source)("eragon").size)
    }
}
