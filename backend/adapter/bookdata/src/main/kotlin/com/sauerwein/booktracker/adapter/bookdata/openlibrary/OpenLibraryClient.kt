package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.adapter.bookdata.resilience.BookSourceException
import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.models.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse

private const val SEARCH_LIMIT = 10

/** `editions…` makes every hit edition-level: one matching edition with its own key and ISBNs. */
private const val SEARCH_FIELDS =
    "key,title,author_name,author_key,first_publish_year,cover_i," +
        "editions,editions.key,editions.title,editions.isbn,editions.publish_year,editions.cover_i,editions.publisher"

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
        val edition = restClient.get()
            .uri("/isbn/{isbn}.json", isbn.value)
            .exchange { _, response -> response.readJsonOrNull<OpenLibraryEdition>() }
            ?: return null

        return toCandidate(edition)
    }

    /** An edition by its own key, e.g. a search hit the user adds. The id came back from our app: validate before it goes into a path. */
    override fun findByRecordId(recordId: SourceRecordId): BookCandidate? {
        val editionId = editionIdOrNull(recordId.value) ?: return null
        val edition = restClient.get()
            .uri("/books/{id}.json", editionId.value)
            .exchange { _, response -> response.readJsonOrNull<OpenLibraryEdition>() }
            ?: return null

        return toCandidate(edition)
    }

    private fun toCandidate(edition: OpenLibraryEdition): BookCandidate? {
        // An author that cannot be resolved is skipped; the mapper drops the record only if none is left.
        // The keys are third-party data: only a validated id goes into a path, never the raw key
        val authors = edition.authors.orEmpty()
            .mapNotNull { it.key?.let(::authorIdOrNull) }
            .take(MAX_RESOLVED_AUTHORS)
            .mapNotNull(::findAuthor)

        return mapper.toCandidate(edition = edition, authors = authors)
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
