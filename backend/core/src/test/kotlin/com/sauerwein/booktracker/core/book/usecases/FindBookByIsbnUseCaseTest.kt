package com.sauerwein.booktracker.core.book.usecases

import com.sauerwein.booktracker.core.book.FakeBookMetadataSource
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.testCandidate
import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FindBookByIsbnUseCaseTest {

    private val isbn = Isbn.of("9783570402931")

    @Test
    fun `returns the candidate the source found`() {
        val candidate = testCandidate(title = "Eragon", isbn = isbn)
        val source = FakeBookMetadataSource(isbnResult = candidate)

        assertEquals(candidate, FindBookByIsbnUseCase(source)(isbn))
    }

    @Test
    fun `looks the book up by ISBN and never runs a text search`() {
        val source = FakeBookMetadataSource(isbnResult = testCandidate(isbn = isbn))

        FindBookByIsbnUseCase(source)(isbn)

        assertEquals(listOf(isbn), source.lookedUpIsbns)
        assertEquals(emptyList(), source.searchedTexts)
    }

    /** The scan flow relies on this: a 404 is what sends the app to the manual form. */
    @Test
    fun `throws NotFoundException when no source has that edition`() {
        val source = FakeBookMetadataSource(isbnResult = null)

        assertFailsWith<NotFoundException> { FindBookByIsbnUseCase(source)(isbn) }
    }
}
