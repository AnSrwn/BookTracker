package com.sauerwein.booktracker.adapter.bookdata.googlebooks

import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceRecordId

private const val ISBN_13 = "ISBN_13"
private const val ISBN_10 = "ISBN_10"
private val YEAR = Regex("""(?<!\d)\d{4}(?!\d)""")

/**
 * A Google volume as a search hit. Its data is shown and never stored (9.7 D6): the descriptor
 * marks it as not persistable, so the app can only add it by its ISBN, which resolves to a
 * CC0 record. A hit without an ISBN is therefore useless and dropped by the search (D8).
 */
class GoogleBooksMapper(private val descriptor: SourceDescriptor) {

    fun toCandidate(volume: GoogleVolume): BookCandidate? {
        val info = volume.volumeInfo ?: return null
        val title = info.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val authors = info.authors.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { Author(it, ref = null) }
            .ifEmpty { return null }

        return BookCandidate(
            title = title,
            authors = authors,
            publisher = info.publisher?.trim()?.takeIf { it.isNotEmpty() },
            isbn = isbn(info.industryIdentifiers.orEmpty()),
            publicationYear = info.publishedDate?.let { YEAR.find(it)?.value?.toInt() },
            pageCount = info.pageCount?.takeIf { it > 0 },
            // Google hands out http links; the app must never load mixed content
            coverUrl = info.imageLinks?.thumbnail?.replaceFirst("http://", "https://"),
            language = Language.ofOrNull(info.language),
            source = descriptor.id,
            sourceId = volume.id?.takeIf { it.isNotBlank() }?.let(::SourceRecordId),
            workKey = null,
            persistableInPublicCatalog = descriptor.persistableInPublicCatalog,
        )
    }

    private fun isbn(identifiers: List<GoogleIndustryIdentifier>): Isbn? =
        listOf(ISBN_13, ISBN_10).firstNotNullOfOrNull { type ->
            identifiers.filter { it.type == type }.firstNotNullOfOrNull { it.identifier?.let(Isbn::ofOrNull) }
        }
}
