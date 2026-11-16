package com.sauerwein.booktracker.adapter.bookdata

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.AbstractClientHttpRequest
import org.springframework.http.client.ClientHttpRequest
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.ClientHttpResponse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * Plays canned responses in order and records every request, so a client's HTTP behaviour
 * (retries, status handling, headers) is tested without a network and without a test library.
 */
class FakeHttp(vararg responses: Canned) : ClientHttpRequestFactory {

    data class Canned(val status: Int, val body: String = "", val contentType: MediaType = MediaType.APPLICATION_JSON)

    data class Recorded(val uri: URI, val headers: HttpHeaders)

    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<Recorded>()

    override fun createRequest(uri: URI, httpMethod: HttpMethod): ClientHttpRequest = object : AbstractClientHttpRequest() {
        private val body = ByteArrayOutputStream()

        override fun getMethod() = httpMethod
        override fun getURI() = uri
        override fun getBodyInternal(headers: HttpHeaders): OutputStream = body

        override fun executeInternal(headers: HttpHeaders): ClientHttpResponse {
            requests += Recorded(uri, HttpHeaders.copyOf(headers))
            val canned = queue.removeFirstOrNull() ?: error("No canned response left for $uri")
            return response(canned)
        }
    }

    private fun response(canned: Canned) = object : ClientHttpResponse {
        override fun getStatusCode(): HttpStatusCode = HttpStatusCode.valueOf(canned.status)
        override fun getStatusText() = ""
        override fun getHeaders() = HttpHeaders().apply { contentType = canned.contentType }
        override fun getBody(): InputStream = ByteArrayInputStream(canned.body.toByteArray())
        override fun close() {}
    }

    companion object {
        fun fixture(name: String): String =
            requireNotNull(FakeHttp::class.java.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()
    }
}
