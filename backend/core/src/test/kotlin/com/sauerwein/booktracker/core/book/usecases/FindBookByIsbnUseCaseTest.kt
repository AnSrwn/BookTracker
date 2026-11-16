package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.*
import com.sauerwein.booktracker.core.book.models.*
import com.sauerwein.booktracker.core.shared.exception.ConflictException
import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import com.sauerwein.booktracker.core.user.UserId
import java.time.Clock
import java.util.*
import kotlin.test.*

class FindBookByIsbnUseCaseTest {

    private val isbn = Isbn.of("9783570402931")
    private val bookRepository = InMemoryBookRepository()

    private fun useCase(source: FakeBookMetadataSource) = FindBookByIsbnUseCase(
        ResolveIsbnUseCase(
            bookRepository = bookRepository,
            bookMetadataSource = source,
            upsertCatalogEntryUseCase = UpsertCatalogEntryUseCase(bookRepository, Clock.systemUTC()),
        )
    )

    @Test
    fun `returns the candidate the source found, with the id of the catalog row it became`() {
        val candidate = testCandidate(title = "Eragon", isbn = isbn)
        val source = FakeBookMetadataSource(isbnResult = candidate)

        val found = useCase(source)(isbn)

        val stored = bookRepository.stored().single()
        assertEquals(candidate.copy(bookId = stored.id, kind = BookKind.CATALOG), found)
    }

    @Test
    fun `looks the book up by ISBN and never runs a text search`() {
        val source = FakeBookMetadataSource(isbnResult = testCandidate(isbn = isbn))

        useCase(source)(isbn)

        assertEquals(listOf(isbn), source.lookedUpIsbns)
        assertEquals(emptyList(), source.searchedTexts)
    }

    @Test
    fun `answers from the catalog without asking the source`() {
        val source = FakeBookMetadataSource(isbnResult = testCandidate(isbn = isbn))
        bookRepository.seed(
            testBook(
                ownerId = null,
                title = "Eragon",
                isbn = isbn,
                source = SourceId.of("dnb"),
                sourceId = SourceRecordId("1055026991")
            )
        )

        val candidate = useCase(source)(isbn)

        assertEquals("Eragon", candidate.title)
        assertEquals(SourceId.of("dnb"), candidate.source)
        assertTrue(candidate.persistableInPublicCatalog)
        assertEquals(bookRepository.stored().single().id, candidate.bookId)
        assertEquals(BookKind.CATALOG, candidate.kind)
        assertEquals(emptyList(), source.lookedUpIsbns)
    }

    /** 9.5's "Done when", at the use-case level. */
    @Test
    fun `resolving the same ISBN twice asks the source once and stores one catalog row`() {
        val source = FakeBookMetadataSource(isbnResult = testCandidate(title = "Eragon", isbn = isbn))
        val findBookByIsbn = useCase(source)

        findBookByIsbn(isbn)
        findBookByIsbn(isbn)

        assertEquals(1, source.lookedUpIsbns.size)
        assertEquals(1, bookRepository.stored().size)
        assertNull(bookRepository.stored().single().ownerId)
    }

    @Test
    fun `returns a candidate from a non-persistable source without storing it`() {
        val candidate =
            testCandidate(isbn = isbn, source = SourceId.of("google_books"), persistableInPublicCatalog = false)
        val source = FakeBookMetadataSource(isbnResult = candidate)
        val findBookByIsbn = useCase(source)

        assertEquals(candidate, findBookByIsbn(isbn))
        assertNull(candidate.bookId)
        assertTrue(bookRepository.stored().isEmpty())

        findBookByIsbn(isbn)
        assertEquals(2, source.lookedUpIsbns.size)
    }

    /** A user's private row is their edit, not a source's answer. */
    @Test
    fun `ignores a private book with that ISBN`() {
        bookRepository.seed(testBook(ownerId = UserId(UUID.randomUUID()), isbn = isbn))
        val source = FakeBookMetadataSource(isbnResult = testCandidate(isbn = isbn))

        useCase(source)(isbn)

        assertEquals(listOf(isbn), source.lookedUpIsbns)
    }

    /** Another source's row claimed the ISBN between lookup and write: the scan must still succeed. */
    @Test
    fun `still returns the candidate when the catalog write conflicts`() {
        val candidate = testCandidate(isbn = isbn)
        val conflicting = object : BookRepository by bookRepository {
            override fun upsertCatalogEntry(book: Book): Book =
                throw ConflictException(code = "DuplicateBook", message = "taken")
        }
        val resolver = ResolveIsbnUseCase(
            bookRepository = conflicting,
            bookMetadataSource = FakeBookMetadataSource(isbnResult = candidate),
            upsertCatalogEntryUseCase = UpsertCatalogEntryUseCase(conflicting, Clock.systemUTC()),
        )

        assertEquals(candidate, FindBookByIsbnUseCase(resolver)(isbn))
    }

    /** The scan flow relies on this: a 404 is what sends the app to the manual form. */
    @Test
    fun `throws NotFoundException when no source has that edition`() {
        val source = FakeBookMetadataSource(isbnResult = null)

        assertFailsWith<NotFoundException> { useCase(source)(isbn) }
    }
}
