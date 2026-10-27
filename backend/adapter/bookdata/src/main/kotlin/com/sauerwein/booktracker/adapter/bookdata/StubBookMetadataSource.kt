package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import org.springframework.stereotype.Component

/**
 * Hardcoded source, so the wiring can be verified without a network client:
 * module registration, component scanning, bean wiring, controller mapping,
 * error translation and auth all become testable on their own.
 *
 * The OpenLibraryClient displaces it as the registered bean; this class stays
 * useful in tests afterwards. Its data is the real DNB record for that ISBN
 * (fixture `dnb-sru-isbn.xml`), so the shape matches what 9.8 will produce.
 */
@Component
class StubBookMetadataSource : BookMetadataSource {

    override val descriptor = SourceDescriptor(
        id = SourceId.of("stub"),
        supportsIsbnLookup = true,
        supportsFreeTextSearch = true,
        providesCovers = false,
        persistableInPublicCatalog = false,
    )

    override fun search(text: String): List<BookCandidate> =
        if (text.contains(SEARCHABLE_TITLE, ignoreCase = true)) listOf(eragon) else emptyList()

    override fun findByIsbn(isbn: Isbn): BookCandidate? =
        eragon.takeIf { it.isbn == isbn }

    private val eragon = BookCandidate(
        title = "Eragon – Das Vermächtnis der Drachenreiter",
        author = "Christopher Paolini",
        publisher = "cbj",
        isbn = Isbn.of("9783570402931"),
        publicationYear = 2014,
        pageCount = 733,
        coverUrl = null,
        source = descriptor.id,
        sourceId = SourceRecordId("1055026991"),
        persistableInPublicCatalog = descriptor.persistableInPublicCatalog,
    )

    private companion object {
        const val SEARCHABLE_TITLE = "eragon"
    }
}
