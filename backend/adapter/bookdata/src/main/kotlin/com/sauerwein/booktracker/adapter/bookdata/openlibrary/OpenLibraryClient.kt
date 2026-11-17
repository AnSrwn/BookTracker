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
        "editions,editions.key,editions.title,editions.isbn,editions.publish_year,editions.cover_i,editions.publisher," +
        "editions.language"

/**
 * Every author is one more request — and at 1 req/s one more second of scan latency. An edition
 * without a resolvable author costs one more for its work: at most 1 + 2 + 1 + 2 requests.
 */
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
    override fun findByRecordId(ref: SourceRef): BookCandidate? {
        if (ref.source != descriptor.id) return null
        val editionId = editionIdOrNull(ref.recordId.value) ?: return null
        val edition = restClient.get()
            .uri("/books/{id}.json", editionId.value)
            .exchange { _, response -> response.readJsonOrNull<OpenLibraryEdition>() }
            ?: return null

        return toCandidate(edition)
    }

    /**
     * The edition's own authors; if it yields no name — it lists none, or none resolves — the
     * authors of its work (9.10 R9). Many editions name their author only on the work (*Pride and
     * Prejudice* `9780141439518`). Work authors are flagged, since the merge ranks them last: a
     * work may credit translators of an anonymous text as authors (R16). With no name from either,
     * the book has no author (Q9).
     */
    private fun toCandidate(edition: OpenLibraryEdition): BookCandidate? {
        // The mapper drops a record without a title; no author or work request is spent on it
        if (edition.title.isNullOrBlank()) return null

        val editionAuthors = resolveAuthors(edition.authors.orEmpty().map { it.key })
        if (editionAuthors.isNotEmpty()) return mapper.toCandidate(edition = edition, authors = editionAuthors)

        val workAuthors = firstWorkIdOrNull(edition)?.let(::findWorkAuthors).orEmpty()
        return mapper.toCandidate(edition = edition, authors = workAuthors, authorsFromWork = true)
    }

    /**
     * An author that cannot be resolved — missing, or without a name — is skipped. The keys are third-party data: only a
     * validated id goes into a path, never the raw key.
     */
    private fun resolveAuthors(keys: List<String?>): List<Author> =
        keys.mapNotNull { it?.let(::authorIdOrNull) }
            .take(MAX_RESOLVED_AUTHORS)
            .mapNotNull(::findAuthor)

    /** A work that is missing (404) has no authors; any other failure fails the lookup, like an author call. */
    private fun findWorkAuthors(workId: SourceRecordId): List<Author> {
        val work = restClient.get()
            .uri("/works/{id}.json", workId.value)
            .exchange { _, response -> response.readJsonOrNull<OpenLibraryWork>() }
            ?: return emptyList()

        return resolveAuthors(work.authors.orEmpty().map { it.author?.key })
    }

    private fun findAuthor(authorId: SourceRecordId): Author? {
        val name = restClient.get()
            .uri("/authors/{id}.json", authorId.value)
            .exchange { _, response -> response.readJsonOrNull<OpenLibraryAuthor>() }
            ?.name
            ?.takeIf { it.isNotBlank() }
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
