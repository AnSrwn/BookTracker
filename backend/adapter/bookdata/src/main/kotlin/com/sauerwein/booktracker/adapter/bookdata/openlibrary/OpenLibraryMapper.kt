package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.core.book.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef

private const val COVERS_BASE_URL = "https://covers.openlibrary.org/b"
private const val EDITION_KEY_PREFIX = "/books/"
private const val AUTHOR_KEY_PREFIX = "/authors/"
private val AUTHOR_ID = Regex("OL[0-9]+A")
private val YEAR = Regex("""(?<!\d)\d{4}(?!\d)""")

class OpenLibraryMapper(private val descriptor: SourceDescriptor) {

    fun toCandidate(edition: OpenLibraryEdition, authors: List<Author>): BookCandidate? {
        val title = edition.title?.takeIf { it.isNotBlank() } ?: return null
        val cleanAuthors = cleanAuthors(authors).ifEmpty { return null }
        val isbn = firstValidIsbn(edition.isbn13) ?: firstValidIsbn(edition.isbn10)

        // `default=false` turns a missing cover into a 404 instead of a 1×1 placeholder GIF
        val coverUrl = isbn
            ?.takeIf { !edition.covers.isNullOrEmpty() }
            ?.let { "$COVERS_BASE_URL/isbn/${it.value}-L.jpg?default=false" }

        return BookCandidate(
            title = title,
            authors = cleanAuthors,
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
        val names = doc.authorName.orEmpty()
        // Parallel arrays: if the lengths differ, pairing by index could credit the wrong person
        val keys = doc.authorKey?.takeIf { it.size == names.size }
        val authors = names.mapIndexed { index, name -> Author(name, keys?.get(index)?.let(::authorIdOrNull)?.let(::authorRef)) }
        val cleanAuthors = cleanAuthors(authors).ifEmpty { return null }

        return BookCandidate(
            title = title,
            authors = cleanAuthors,
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

    fun authorRef(authorId: SourceRecordId) = SourceRef(descriptor.id, authorId)

    private fun cleanAuthors(authors: List<Author>): List<Author> =
        authors
            .map { it.copy(name = it.name.trim()) }
            .filter { it.name.isNotEmpty() }
            .distinctBy { it.name }
}

/** Edition records prefix the key (`/authors/OL1425963A`), search docs do not. */
internal fun authorIdOrNull(key: String): SourceRecordId? =
    key.removePrefix(AUTHOR_KEY_PREFIX).takeIf { AUTHOR_ID.matches(it) }?.let(::SourceRecordId)
