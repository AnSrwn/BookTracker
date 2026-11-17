package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.sauerwein.booktracker.adapter.bookdata.BookDataProperties
import com.sauerwein.booktracker.adapter.bookdata.BookSourceRegistration
import com.sauerwein.booktracker.adapter.bookdata.resilience.ResilientBookMetadataSource
import com.sauerwein.booktracker.adapter.bookdata.throttledRestClient
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
        val provider =
            requireNotNull(properties.openLibrary) { "booktracker.bookdata.open-library is enabled but not configured" }

        return BookSourceRegistration(
            ResilientBookMetadataSource(
                delegate = OpenLibraryClient(
                    restClient = throttledRestClient(
                        sourceName = sourceName,
                        userAgent = properties.userAgent,
                        provider = provider,
                        rateLimiterRegistry = rateLimiterRegistry
                    )
                ),
                circuitBreaker = circuitBreakerRegistry.circuitBreaker(sourceName),
                meterRegistry = meterRegistry,
            ),
        )
    }
}
