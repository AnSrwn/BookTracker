package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.models.*

/**
 * Hardcoded source for tests that need a well-behaved provider without a network
 * client. It proved the wiring in 9.2 and is no longer a bean.
 *
 * Its data is the real DNB record for that ISBN (fixture `dnb-sru-isbn.xml`),
 * so the shape matches what 9.8 will produce.
 */
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

    override fun findByRecordId(ref: SourceRef): BookCandidate? =
        eragon.takeIf { ref.source == descriptor.id && it.sourceId == ref.recordId }

    private val eragon = BookCandidate(
        title = "Eragon – Das Vermächtnis der Drachenreiter",
        authors = listOf(Author("Christopher Paolini", SourceRef(SourceId.of("dnb"), SourceRecordId("129384240")))),
        publisher = "cbj",
        isbn = Isbn.of("9783570402931"),
        publicationYear = 2014,
        pageCount = 733,
        coverUrl = null,
        language = Language.of("de"),
        source = descriptor.id,
        sourceId = SourceRecordId("1055026991"),
        workKey = null,
        persistableInPublicCatalog = descriptor.persistableInPublicCatalog,
    )

    private companion object {
        const val SEARCHABLE_TITLE = "eragon"
    }
}
