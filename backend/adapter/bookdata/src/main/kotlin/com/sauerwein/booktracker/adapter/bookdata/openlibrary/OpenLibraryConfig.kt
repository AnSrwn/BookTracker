package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

private const val OPEN_LIBRARY_BASE_URL = "https://openlibrary.org"

@Configuration
class OpenLibraryConfig {

    companion object {
        fun restClientBuilder(userAgent: String): RestClient.Builder {
            return RestClient.builder()
                .baseUrl(OPEN_LIBRARY_BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestFactory(
                    JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                    )
                )
        }
    }

    @Bean
    fun openLibraryRestClient(@Value($$"${booktracker.bookdata.user-agent}") userAgent: String): RestClient {
        return restClientBuilder(userAgent).build()
    }
}
