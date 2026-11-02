package com.sauerwein.booktracker.adapter.bookdata.resilience

import com.sauerwein.booktracker.adapter.bookdata.fixture
import com.sauerwein.booktracker.adapter.bookdata.openlibrary.OpenLibraryClient
import com.sauerwein.booktracker.adapter.bookdata.openlibrary.OpenLibraryConfig
import com.sauerwein.booktracker.core.book.models.Isbn
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RateLimitingInterceptorTest {

    /** One permit per hour and no waiting, so every request beyond the budget is rejected at once. */
    private fun limiter(permits: Int) = RateLimiter.of(
        "test",
        RateLimiterConfig.custom()
            .limitForPeriod(permits)
            .limitRefreshPeriod(Duration.ofHours(1))
            .timeoutDuration(Duration.ZERO)
            .build(),
    )

    @Test
    fun `rejects a request over the budget before it reaches the wire`() {
        val builder = OpenLibraryConfig.restClientBuilder("test", limiter(permits = 1))
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = OpenLibraryClient(builder.build())

        server.expect(once()) { assertEquals("/search.json", it.uri.path) }
            .andRespond(withSuccess("""{"docs":[]}""", MediaType.APPLICATION_JSON))

        client.search("eragon")
        assertFailsWith<RequestNotPermitted> { client.search("eragon") }
        server.verify()
    }

    /** An ISBN lookup is two requests, so throttling per port call would let 2 req/s through. */
    @Test
    fun `charges one permit per request, so an ISBN lookup costs two`() {
        val rateLimiter = limiter(permits = 2)
        val builder = OpenLibraryConfig.restClientBuilder("test", rateLimiter)
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = OpenLibraryClient(builder.build())

        server.expect(requestTo("https://openlibrary.org/isbn/9780375826689.json"))
            .andRespond(withSuccess(fixture("openlibrary-isbn-hit.json"), MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://openlibrary.org/authors/OL1425963A.json"))
            .andRespond(withSuccess(fixture("openlibrary-author.json"), MediaType.APPLICATION_JSON))

        client.findByIsbn(Isbn.of("9780375826689"))

        server.verify()
        assertEquals(0, rateLimiter.metrics.availablePermissions)
    }
}
