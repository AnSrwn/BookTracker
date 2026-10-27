package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.core.book.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceRecordId

private const val COVERS_BASE_URL = "https://covers.openlibrary.org/b"
private const val EDITION_KEY_PREFIX = "/books/"
private val YEAR = Regex("""(?<!\d)\d{4}(?!\d)""")

class OpenLibraryMapper(private val descriptor: SourceDescriptor) {

    fun toCandidate(edition: OpenLibraryEdition, authorName: String?): BookCandidate? {
        val title = edition.title?.takeIf { it.isNotBlank() } ?: return null
        val author = authorName?.takeIf { it.isNotBlank() } ?: return null
        val isbn = firstValidIsbn(edition.isbn13) ?: firstValidIsbn(edition.isbn10)

        // `default=false` turns a missing cover into a 404 instead of a 1×1 placeholder GIF
        val coverUrl = isbn
            ?.takeIf { !edition.covers.isNullOrEmpty() }
            ?.let { "$COVERS_BASE_URL/isbn/${it.value}-L.jpg?default=false" }

        return BookCandidate(
            title = title,
            author = author,
            publisher = edition.publishers?.firstOrNull { it.isNotBlank() },
            isbn = isbn,
            publicationYear = edition.publishDate?.let { YEAR.find(it)?.value?.toInt() },
            pageCount = edition.numberOfPages?.takeIf { it > 0 },
            coverUrl = coverUrl,
            source = descriptor.id,
            sourceId = edition.key?.removePrefix(EDITION_KEY_PREFIX)?.takeIf { it.isNotBlank() }?.let(::SourceRecordId),
            persistableInPublicCatalog = descriptor.persistableInPublicCatalog,
        )
    }

    fun toCandidate(doc: OpenLibrarySearchDoc): BookCandidate? {
        val title = doc.title?.takeIf { it.isNotBlank() } ?: return null
        val author = doc.authorName?.firstOrNull { it.isNotBlank() } ?: return null

        return BookCandidate(
            title = title,
            author = author,
            publisher = null,
            isbn = null,
            publicationYear = doc.firstPublishYear,
            pageCount = null,
            coverUrl = doc.coverId?.let { "$COVERS_BASE_URL/id/$it-L.jpg?default=false" },
            source = descriptor.id,
            sourceId = doc.coverEditionKey?.takeIf { it.isNotBlank() }?.let(::SourceRecordId),
            persistableInPublicCatalog = descriptor.persistableInPublicCatalog,
        )
    }

    private fun firstValidIsbn(values: List<String>?): Isbn? =
        values?.firstNotNullOfOrNull { Isbn.ofOrNull(rawValue = it) }
}
