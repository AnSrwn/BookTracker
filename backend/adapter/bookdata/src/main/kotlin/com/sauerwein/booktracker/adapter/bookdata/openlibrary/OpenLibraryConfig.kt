package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.adapter.bookdata.resilience.RateLimitingInterceptor
import com.sauerwein.booktracker.adapter.bookdata.resilience.ResilientBookMetadataSource
import com.sauerwein.booktracker.core.book.BookMetadataSource
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

private const val OPEN_LIBRARY_BASE_URL = "https://openlibrary.org"
private val CONNECT_TIMEOUT = Duration.ofSeconds(1)
private val READ_TIMEOUT = Duration.ofSeconds(3)

/**
 * 1 req/s although the contact User-Agent would allow 3: the limit is per IP, and being
 * wrong is answered with blocking rather than a clean error. A burst waits up to the
 * timeout for its turn instead of being rejected.
 */
private val RATE_LIMITER_CONFIG = RateLimiterConfig.custom()
    .limitForPeriod(1)
    .limitRefreshPeriod(Duration.ofSeconds(1))
    .timeoutDuration(Duration.ofSeconds(5))
    .build()

@Configuration
class OpenLibraryConfig {

    companion object {
        fun restClientBuilder(userAgent: String, rateLimiter: RateLimiter): RestClient.Builder {
            val httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()

            return RestClient.builder()
                .baseUrl(OPEN_LIBRARY_BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestInterceptor(RateLimitingInterceptor(rateLimiter))
                .requestFactory(JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(READ_TIMEOUT) })
        }
    }

    private val sourceName = OpenLibraryClient.DESCRIPTOR.id.value

    @Bean
    fun openLibraryRestClient(
        @Value($$"${booktracker.bookdata.user-agent}") userAgent: String,
        rateLimiterRegistry: RateLimiterRegistry,
    ): RestClient {
        val rateLimiter = rateLimiterRegistry.rateLimiter(sourceName, RATE_LIMITER_CONFIG)
        return restClientBuilder(userAgent, rateLimiter).build()
    }

    @Bean
    fun openLibrarySource(
        openLibraryRestClient: RestClient,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        meterRegistry: MeterRegistry,
    ): BookMetadataSource {
        return ResilientBookMetadataSource(
            delegate = OpenLibraryClient(openLibraryRestClient),
            circuitBreaker = circuitBreakerRegistry.circuitBreaker(sourceName),
            meterRegistry = meterRegistry,
        )
    }
}
