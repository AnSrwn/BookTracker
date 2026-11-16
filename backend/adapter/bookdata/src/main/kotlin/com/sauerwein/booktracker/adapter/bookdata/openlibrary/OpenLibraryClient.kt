package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.adapter.bookdata.resilience.BookSourceException
import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse

private const val SEARCH_LIMIT = 10
private const val SEARCH_FIELDS = "key,title,author_name,author_key,first_publish_year,cover_i,cover_edition_key"

/** Every author is one more request — and at 1 req/s one more second of scan latency. */
private const val MAX_RESOLVED_AUTHORS = 2

class OpenLibraryException(message: String) : BookSourceException(message)

/**
 * Plain client: throttling sits in the RestClient (see OpenLibraryConfig), circuit
 * breaking and metrics in the ResilientBookMetadataSource wrapped around it.
 */
class OpenLibraryClient(private val restClient: RestClient) : BookMetadataSource {

    companion object {
        val DESCRIPTOR = SourceDescriptor(
            id = SourceId.of("open_library"),
            supportsIsbnLookup = true,
            supportsFreeTextSearch = true,
            providesCovers = true,
            persistableInPublicCatalog = true,
        )
    }

    override val descriptor = DESCRIPTOR

    private val mapper = OpenLibraryMapper(descriptor)

    override fun search(text: String): List<BookCandidate> {
        val response = restClient.get()
            .uri("/search.json?q={q}&limit={limit}&fields={fields}", text, SEARCH_LIMIT, SEARCH_FIELDS)
            .exchange { _, response -> response.readJsonOrNull<OpenLibrarySearchResponse>() }

        return response?.docs.orEmpty().mapNotNull(mapper::toCandidate)
    }

    override fun findByIsbn(isbn: Isbn): BookCandidate? {
        val editionResponse = restClient.get()
            .uri("/isbn/{isbn}.json", isbn.value)
            .exchange { _, response -> response.readJsonOrNull<OpenLibraryEdition>() }
            ?: return null

        // An author that cannot be resolved is skipped; the mapper drops the record only if none is left.
        // The keys are third-party data: only a validated id goes into a path, never the raw key
        val authors = editionResponse.authors.orEmpty()
            .mapNotNull { it.key?.let(::authorIdOrNull) }
            .take(MAX_RESOLVED_AUTHORS)
            .mapNotNull(::findAuthor)

        return mapper.toCandidate(editionResponse, authors)
    }

    private fun findAuthor(authorId: SourceRecordId): Author? {
        val name = restClient.get()
            .uri("/authors/{id}.json", authorId.value)
            .exchange { _, response -> response.readJsonOrNull<OpenLibraryAuthor>() }
            ?.name
            ?: return null

        return Author(name, mapper.authorRef(authorId))
    }

    /**
     * A miss on `/isbn/{isbn}.json` answers with an HTML page, so the body is only read
     * once status and content type say it is JSON.
     */
    private inline fun <reified T : Any> ConvertibleClientHttpResponse.readJsonOrNull(): T? {
        if (statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) return null

        if (!statusCode.is2xxSuccessful) {
            throw OpenLibraryException("Open Library answered ${statusCode.value()}")
        }

        val contentType = headers.contentType

        if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            throw OpenLibraryException("Open Library answered with content type $contentType instead of JSON")
        }

        return bodyTo(T::class.java)
    }
}
