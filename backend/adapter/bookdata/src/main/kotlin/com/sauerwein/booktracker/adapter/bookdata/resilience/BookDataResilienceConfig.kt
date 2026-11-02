package com.sauerwein.booktracker.adapter.bookdata.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClientException
import java.time.Duration

/** Registries shared by all sources; each source registers its instances under its descriptor id. */
@Configuration
class BookDataResilienceConfig {

    companion object {
        fun circuitBreakerConfig(): CircuitBreakerConfig = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordExceptions(BookSourceException::class.java, RestClientException::class.java)
            // Our own throttle firing says nothing about the source's health
            .ignoreExceptions(RequestNotPermitted::class.java)
            .build()
    }

    @Bean
    fun rateLimiterRegistry(meterRegistry: MeterRegistry): RateLimiterRegistry =
        RateLimiterRegistry.ofDefaults().also {
            TaggedRateLimiterMetrics.ofRateLimiterRegistry(it).bindTo(meterRegistry)
        }

    @Bean
    fun circuitBreakerRegistry(meterRegistry: MeterRegistry): CircuitBreakerRegistry =
        CircuitBreakerRegistry.of(circuitBreakerConfig()).also {
            TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(it).bindTo(meterRegistry)
        }
}
