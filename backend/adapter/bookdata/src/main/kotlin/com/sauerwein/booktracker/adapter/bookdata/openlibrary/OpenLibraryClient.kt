package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse

private const val SEARCH_LIMIT = 10
private const val SEARCH_FIELDS = "title,author_name,first_publish_year,cover_i,cover_edition_key"
private const val AUTHOR_KEY_PREFIX = "/authors/"
private val AUTHOR_ID = Regex("OL[0-9]+A")

class OpenLibraryException(message: String) : RuntimeException(message)

/**
 * Not a `@Component` yet: the stub stays the registered source until 9.4 puts
 * the rate limiter in front of this client.
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

        val authorName = editionResponse.authors?.firstOrNull()?.key?.let(::findAuthorName)

        return mapper.toCandidate(editionResponse, authorName)
    }

    private fun findAuthorName(authorKey: String): String? {
        // The key comes from third-party data; only an id goes into the path, never the raw key
        val authorId = authorKey.removePrefix(AUTHOR_KEY_PREFIX).takeIf { AUTHOR_ID.matches(it) } ?: return null

        return restClient.get()
            .uri("/authors/{id}.json", authorId)
            .exchange { _, response -> response.readJsonOrNull<OpenLibraryAuthor>() }
            ?.name
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
