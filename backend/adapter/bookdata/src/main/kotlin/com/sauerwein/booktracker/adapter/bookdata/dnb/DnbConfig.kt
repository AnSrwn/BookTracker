package com.sauerwein.booktracker.adapter.bookdata.dnb

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
 * DNB publishes no rate limit; `rate-per-second: 1` is our own politeness towards a public
 * institution's service, not a stated ceiling (architecture §7).
 */
@Configuration
@ConditionalOnProperty(prefix = "booktracker.bookdata.dnb", name = ["enabled"], havingValue = "true")
class DnbConfig {

    private val sourceName = DnbSruClient.DESCRIPTOR.id.value

    @Bean
    fun dnbSource(
        properties: BookDataProperties,
        rateLimiterRegistry: RateLimiterRegistry,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        meterRegistry: MeterRegistry,
    ): BookSourceRegistration {
        val provider = requireNotNull(properties.dnb) { "booktracker.bookdata.dnb is enabled but not configured" }

        return BookSourceRegistration(
            ResilientBookMetadataSource(
                delegate = DnbSruClient(
                    throttledRestClient(
                        sourceName,
                        properties.userAgent,
                        provider,
                        rateLimiterRegistry
                    )
                ),
                circuitBreaker = circuitBreakerRegistry.circuitBreaker(sourceName),
                meterRegistry = meterRegistry,
            ),
        )
    }
}
