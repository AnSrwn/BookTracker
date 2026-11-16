package com.sauerwein.booktracker.adapter.bookdata.googlebooks

import com.sauerwein.booktracker.adapter.bookdata.resilience.BookSourceException
import com.sauerwein.booktracker.adapter.bookdata.resilience.QuotaExhaustedException
import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceDescriptor
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRef
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse
import java.time.Duration

private const val SEARCH_URI =
    "/books/v1/volumes?q={q}&country={country}&printType=books&maxResults={maxResults}&fields={fields}"
private const val MAX_RESULTS = 10

/** Exactly what the mapper reads, so a response stays small. */
private const val FIELDS =
    "items(id,volumeInfo(title,authors,publisher,publishedDate,pageCount,industryIdentifiers,imageLinks/thumbnail))"

/** `503` is common at Google (5 of 8 calls in one run in October 2026): retried, unlike a spent quota. */
private const val MAX_ATTEMPTS = 3

private val DAILY_QUOTA_REASONS = setOf("dailyLimitExceeded", "dailyLimitExceededUnreg")
private val QUOTA_REASONS = DAILY_QUOTA_REASONS + setOf("quotaExceeded", "rateLimitExceeded", "userRateLimitExceeded")
private const val QUOTA_STATUS = "RESOURCE_EXHAUSTED"
private const val MAX_MESSAGE_LENGTH = 200

class GoogleBooksException(message: String) : BookSourceException(message)

/**
 * Google Books **finds** books; it never describes the ones we keep (ADR-007, 9.7 D6). Free-text
 * search only: the scan path never calls it and spends none of the daily budget (D7).
 *
 * The API key travels in a header, never in the URL, so it cannot end up in a log line.
 * Circuit breaking and metrics sit in the ResilientBookMetadataSource wrapped around it.
 */
class GoogleBooksClient(
    private val restClient: RestClient,
    private val country: String,
    private val quotaGuard: DailyQuotaGuard,
    private val retryBackoff: Duration = Duration.ofMillis(300),
) : BookMetadataSource {

    companion object {
        val DESCRIPTOR = SourceDescriptor(
            id = SourceId.of("google_books"),
            supportsIsbnLookup = false,
            supportsFreeTextSearch = true,
            providesCovers = true,
            // Google's terms forbid permanent copies (Google APIs ToS §5(e)); the DB allowlist backstops this
            persistableInPublicCatalog = false,
        )
    }

    override val descriptor = DESCRIPTOR

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = GoogleBooksMapper(descriptor)

    override fun search(text: String): List<BookCandidate> {
        var attempt = 1
        while (true) {
            if (quotaGuard.isClosed()) throw QuotaExhaustedException("Google Books quota is spent")

            val volumes = restClient.get()
                .uri(SEARCH_URI, text, country, MAX_RESULTS, FIELDS)
                .exchange { _, response -> read(response) }

            if (volumes != null) return volumes.mapNotNull(mapper::toCandidate)
            if (attempt == MAX_ATTEMPTS) throw GoogleBooksException("Google Books answered 503 $MAX_ATTEMPTS times")

            Thread.sleep(retryBackoff.multipliedBy(attempt.toLong()))
            attempt++
        }
    }

    /** A bug if called: the descriptor says this provider cannot look up ISBNs. */
    override fun findByIsbn(isbn: Isbn): BookCandidate? =
        throw UnsupportedOperationException("Google Books does no ISBN lookup (supportsIsbnLookup = false)")

    /** A Google record is never fetched to be stored (D6): a Google hit is added by its ISBN instead. */
    override fun findByRecordId(ref: SourceRef): BookCandidate? = null

    /** The volumes, or `null` for a `503` worth retrying. */
    private fun read(response: ConvertibleClientHttpResponse): List<GoogleVolume>? {
        val status = response.statusCode
        return when {
            status.is2xxSuccessful -> response.bodyTo(GoogleVolumesResponse::class.java)?.items.orEmpty()
            status.isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE) -> null
            status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS) -> throw quotaSpent(errorOf(response))
            status.isSameCodeAs(HttpStatus.FORBIDDEN) -> {
                val error = errorOf(response)
                // A 403 is a spent quota only if Google says so; "cannot determine user location" is not
                if (error.isQuota()) throw quotaSpent(error)
                throw GoogleBooksException("Google Books answered 403: ${error?.message.orEmpty().take(MAX_MESSAGE_LENGTH)}")
            }
            else -> throw GoogleBooksException("Google Books answered ${status.value()}")
        }
    }

    private fun quotaSpent(error: GoogleError?): QuotaExhaustedException {
        val daily = error?.errors.orEmpty().any { it.reason in DAILY_QUOTA_REASONS } ||
            error?.message.orEmpty().contains("per day", ignoreCase = true)
        val until = quotaGuard.trip(daily)
        log.warn("Google Books quota spent ({}): leaving Google out of search until {}", if (daily) "daily" else "short-term", until)
        return QuotaExhaustedException("Google Books quota is spent until $until")
    }

    private fun errorOf(response: ConvertibleClientHttpResponse): GoogleError? =
        runCatching { response.bodyTo(GoogleErrorResponse::class.java)?.error }.getOrNull()

    private fun GoogleError?.isQuota(): Boolean =
        this != null && (status == QUOTA_STATUS || errors.orEmpty().any { it.reason in QUOTA_REASONS })
}
