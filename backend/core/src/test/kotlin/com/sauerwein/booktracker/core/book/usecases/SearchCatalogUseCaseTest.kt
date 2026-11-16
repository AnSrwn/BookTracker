package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.FakeBookMetadataSource
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MIN_LOCAL_HITS = 2

class SearchCatalogUseCaseTest {

    private val user = UserId(UUID.randomUUID())
    private val otherUser = UserId(UUID.randomUUID())
    private val bookRepository = InMemoryBookRepository()

    private val upsertCatalogEntry = UpsertCatalogEntryUseCase(bookRepository, Clock.systemUTC())

    private fun useCase(source: FakeBookMetadataSource) = SearchCatalogUseCase(
        resolveIsbnUseCase = ResolveIsbnUseCase(bookRepository, source, upsertCatalogEntry),
        upsertCatalogEntryUseCase = upsertCatalogEntry,
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

    /** D19: every hit the app may add carries an id, so the app never decides which catalog book to save. */
    @Test
    fun `stores a source hit with an ISBN in the catalog and returns it with its id`() {
        val source = FakeBookMetadataSource(
            searchResults = listOf(
                testCandidate(
                    title = "Eragon",
                    isbn = Isbn.of("9780375826689")
                )
            )
        )

        val hit = useCase(source)("eragon").single()

        val stored = bookRepository.stored().single()
        assertNull(stored.ownerId)
        assertEquals(stored.id, hit.bookId)
        assertEquals(BookKind.CATALOG, hit.kind)
    }

    /** D22: a hit without an ISBN could never be matched to a scan, so it stays out of the catalog. */
    @Test
    fun `never stores a source hit without an ISBN`() {
        val source = FakeBookMetadataSource(searchResults = listOf(testCandidate(title = "Eragon", isbn = null)))

        val hit = useCase(source)("eragon").single()

        assertTrue(bookRepository.stored().isEmpty())
        assertNull(hit.bookId)
        assertNull(hit.kind)
    }

    @Test
    fun `never stores a hit from a source the licence keeps out of the catalog`() {
        val hit = testCandidate(
            isbn = Isbn.of("9780375826689"),
            source = SourceId.of("google_books"),
            persistableInPublicCatalog = false
        )
        val source = FakeBookMetadataSource(searchResults = listOf(hit))

        assertNull(useCase(source)("dune").single().bookId)
        assertTrue(bookRepository.stored().isEmpty())
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

    /** Another edition record claims an ISBN the catalog already holds: one row, listed once. */
    @Test
    fun `a source hit whose ISBN the catalog already holds comes back as that row, once`() {
        val isbn = Isbn.of("9780375826689")
        val catalogBook = testBook(
            ownerId = null,
            title = "Eragon",
            isbn = isbn,
            source = SourceId.of("dnb"),
            sourceId = SourceRecordId("1055026991")
        )
        bookRepository.seed(catalogBook)
        val source =
            FakeBookMetadataSource(searchResults = listOf(testCandidate(title = "Eragon (Open Library)", isbn = isbn)))

        val hits = useCase(source)("eragon")

        assertEquals(listOf(catalogBook.id), hits.map { it.bookId })
        assertEquals(1, bookRepository.stored().size)
    }
}
