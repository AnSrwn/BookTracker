package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.FakeBookMetadataSource
import com.sauerwein.booktracker.core.book.InMemoryBookRepository
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
import kotlin.test.assertTrue

private const val MIN_LOCAL_HITS = 2

class SearchCatalogUseCaseTest {

    private val user = UserId(UUID.randomUUID())
    private val otherUser = UserId(UUID.randomUUID())
    private val bookRepository = InMemoryBookRepository()

    private fun useCase(source: FakeBookMetadataSource) = SearchCatalogUseCase(
        resolveIsbnUseCase = ResolveIsbnUseCase(bookRepository, source, CatalogUpsertUseCase(bookRepository, Clock.systemUTC())),
        bookRepository = bookRepository,
        bookMetadataSource = source,
        currentUserProvider = { user },
        minLocalHits = MIN_LOCAL_HITS,
    )

    @Test
    fun `runs a text search for ordinary words`() {
        val hits = listOf(testCandidate(title = "Eragon"), testCandidate(title = "Eldest"))
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

        assertEquals(listOf(candidate), useCase(source)("978-3-570-40293-1"))
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
        bookRepository.add(testBook(ownerId = null, title = "Eragon"))
        bookRepository.add(testBook(ownerId = user, title = "Eragon, signed"))
        val source = FakeBookMetadataSource(searchResults = listOf(testCandidate(title = "Eragon")))

        val titles = useCase(source)("eragon").map { it.title }

        assertEquals(listOf("Eragon", "Eragon, signed"), titles)
        assertEquals(emptyList(), source.searchedTexts)
    }

    @Test
    fun `puts local hits first and drops source hits for the same record`() {
        val source = SourceId.of("open_library")
        bookRepository.add(testBook(ownerId = null, title = "Eragon", source = source, sourceId = SourceRecordId("OL1M")))
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
        bookRepository.add(testBook(ownerId = null, title = "Eragon", author = "Christopher Paolini"))
        val source = FakeBookMetadataSource()

        assertEquals(listOf("Eragon"), useCase(source)("eragon paolini").map { it.title })
    }

    @Test
    fun `never returns another user's private book`() {
        bookRepository.add(testBook(ownerId = otherUser, title = "Eragon"))
        bookRepository.add(testBook(ownerId = otherUser, title = "Eragon II"))
        val source = FakeBookMetadataSource()

        assertTrue(useCase(source)("eragon").isEmpty())
    }

    @Test
    fun `never stores text search results`() {
        val source = FakeBookMetadataSource(searchResults = listOf(testCandidate(title = "Eragon")))

        useCase(source)("eragon")

        assertTrue(bookRepository.stored().isEmpty())
    }
}
