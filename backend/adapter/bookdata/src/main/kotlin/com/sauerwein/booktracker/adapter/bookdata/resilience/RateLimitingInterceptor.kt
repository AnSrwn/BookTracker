package com.sauerwein.booktracker.adapter.bookdata.resilience

import io.github.resilience4j.ratelimiter.RateLimiter
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/**
 * Throttles on the wire rather than per port call: an ISBN lookup costs two requests
 * (edition, then author), and a limiter around `findByIsbn` would let both through
 * on one permit. Waits for a permit and throws `RequestNotPermitted` once the
 * limiter's timeout is exceeded.
 */
class RateLimitingInterceptor(private val rateLimiter: RateLimiter) : ClientHttpRequestInterceptor {

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        RateLimiter.waitForPermission(rateLimiter)
        return execution.execute(request, body)
    }
}
