package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.adapter.bookdata.resilience.RateLimitingInterceptor
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/** A burst waits up to this long for its turn instead of being rejected. */
private val RATE_LIMIT_WAIT = Duration.ofSeconds(5)

/** Without a configured rate, a provider is held to one request per second. */
private const val DEFAULT_RATE_PER_SECOND = 1

/**
 * The RestClient for a provider limited per second: the limiter sits on the wire, named after the
 * source, so one port call that costs several requests needs a permit for each. Redirects are
 * followed explicitly — Open Library answers an ISBN with a `302` to the edition.
 */
fun throttledRestClient(
    sourceName: String,
    userAgent: String,
    provider: ProviderProperties,
    rateLimiterRegistry: RateLimiterRegistry,
): RestClient {
    val rateLimiter = rateLimiterRegistry.rateLimiter(
        sourceName,
        RateLimiterConfig.custom()
            .limitForPeriod(provider.ratePerSecond ?: DEFAULT_RATE_PER_SECOND)
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
