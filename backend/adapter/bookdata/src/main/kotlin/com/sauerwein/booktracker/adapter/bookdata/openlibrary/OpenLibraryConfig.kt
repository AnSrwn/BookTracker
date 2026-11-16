package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.adapter.bookdata.BookDataProperties
import com.sauerwein.booktracker.adapter.bookdata.BookSourceRegistration
import com.sauerwein.booktracker.adapter.bookdata.ProviderProperties
import com.sauerwein.booktracker.adapter.bookdata.resilience.RateLimitingInterceptor
import com.sauerwein.booktracker.adapter.bookdata.resilience.ResilientBookMetadataSource
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/** A burst waits up to this long for its turn instead of being rejected. */
private val RATE_LIMIT_WAIT = Duration.ofSeconds(5)

/**
 * `rate-per-second` is 1 although the contact User-Agent would allow 3: the limit is per IP,
 * and being wrong is answered with blocking rather than a clean error.
 */
@Configuration
@ConditionalOnProperty(prefix = "booktracker.bookdata.open-library", name = ["enabled"], havingValue = "true")
class OpenLibraryConfig {

    private val sourceName = OpenLibraryClient.DESCRIPTOR.id.value

    @Bean
    fun openLibrarySource(
        properties: BookDataProperties,
        rateLimiterRegistry: RateLimiterRegistry,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        meterRegistry: MeterRegistry,
    ): BookSourceRegistration {
        val provider = requireNotNull(properties.openLibrary) { "booktracker.bookdata.open-library is enabled but not configured" }

        return BookSourceRegistration(
            ResilientBookMetadataSource(
                delegate = OpenLibraryClient(restClient(properties.userAgent, provider, rateLimiterRegistry)),
                circuitBreaker = circuitBreakerRegistry.circuitBreaker(sourceName),
                meterRegistry = meterRegistry,
            ),
        )
    }

    private fun restClient(userAgent: String, provider: ProviderProperties, rateLimiterRegistry: RateLimiterRegistry): RestClient {
        val rateLimiter = rateLimiterRegistry.rateLimiter(
            sourceName,
            RateLimiterConfig.custom()
                .limitForPeriod(provider.ratePerSecond ?: 1)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(RATE_LIMIT_WAIT)
                .build(),
        )
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(provider.connectTimeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        return RestClient.builder()
            .baseUrl(provider.baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .requestInterceptor(RateLimitingInterceptor(rateLimiter))
            .requestFactory(JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(provider.readTimeout) })
            .build()
    }
}
