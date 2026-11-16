package com.sauerwein.booktracker.adapter.bookdata.googlebooks

import com.sauerwein.booktracker.adapter.bookdata.BookDataProperties
import com.sauerwein.booktracker.adapter.bookdata.BookSourceRegistration
import com.sauerwein.booktracker.adapter.bookdata.ProviderProperties
import com.sauerwein.booktracker.adapter.bookdata.resilience.ResilientBookMetadataSource
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Clock

const val GOOGLE_BOOKS_API_KEY_VARIABLE = "BOOKTRACKER_GOOGLE_BOOKS_API_KEY"
private const val API_KEY_HEADER = "X-Goog-Api-Key"

/** Without it, a container whose IP Google cannot place gets `403 Cannot determine user location`. */
private const val DEFAULT_COUNTRY = "DE"

/**
 * The key is mandatory, not an optimisation: without one, requests share an anonymous pool that
 * is in practice already spent, and fail as a `429` that looks like our own quota (9.7 D3).
 */
fun requireGoogleBooksApiKey(provider: ProviderProperties): String {
    val key = provider.apiKey
    check(!key.isNullOrBlank()) {
        "Google Books is enabled but has no API key: set $GOOGLE_BOOKS_API_KEY_VARIABLE, " +
            "or disable it with booktracker.bookdata.google-books.enabled=false (BOOKTRACKER_GOOGLE_BOOKS_ENABLED=false)"
    }
    return key
}

/** The key goes into a header, never into the URL, so no log line can carry it. */
fun googleBooksRestClient(
    baseUrl: String,
    userAgent: String,
    apiKey: String,
    requestFactory: ClientHttpRequestFactory,
): RestClient = RestClient.builder()
    .baseUrl(baseUrl)
    .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
    .defaultHeader(API_KEY_HEADER, apiKey)
    .requestFactory(requestFactory)
    .build()

/** No per-second limiter: Google's binding limit is the daily budget, which the quota guard handles. */
@Configuration
@ConditionalOnProperty(prefix = "booktracker.bookdata.google-books", name = ["enabled"], havingValue = "true")
class GoogleBooksConfig {

    @Bean
    fun googleBooksSource(
        properties: BookDataProperties,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        meterRegistry: MeterRegistry,
        clock: Clock,
    ): BookSourceRegistration {
        val provider = requireNotNull(properties.googleBooks) { "booktracker.bookdata.google-books is enabled but not configured" }
        val apiKey = requireGoogleBooksApiKey(provider)

        val httpClient = HttpClient.newBuilder().connectTimeout(provider.connectTimeout).build()
        val restClient = googleBooksRestClient(
            baseUrl = provider.baseUrl,
            userAgent = properties.userAgent,
            apiKey = apiKey,
            requestFactory = JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(provider.readTimeout) },
        )

        return BookSourceRegistration(
            ResilientBookMetadataSource(
                delegate = GoogleBooksClient(
                    restClient = restClient,
                    country = provider.country ?: DEFAULT_COUNTRY,
                    quotaGuard = DailyQuotaGuard(clock),
                ),
                circuitBreaker = circuitBreakerRegistry.circuitBreaker(GoogleBooksClient.DESCRIPTOR.id.value),
                meterRegistry = meterRegistry,
            ),
        )
    }
}
