package com.sauerwein.booktracker.adapter.bookdata

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * One uniform block per provider, so adding one is filling in a block (architecture §7).
 * Each provider is switched on by its own `enabled` (`@ConditionalOnProperty` on its config);
 * a block that is missing or disabled leaves no trace in the running application.
 */
@ConfigurationProperties("booktracker.bookdata")
data class BookDataProperties(
    /** Sent to every provider. Open Library asks for a contact address in it. */
    val userAgent: String,
    val order: Order = Order(),
    val openLibrary: ProviderProperties? = null,
    val googleBooks: ProviderProperties? = null,
) {
    /**
     * Source ids in the order their answers are used. Providers that are not listed come after
     * the listed ones, by id; listed ids that are not running are ignored. Until 9.9 merges
     * fields, the order only decides which hits come first and which lookup is asked first.
     */
    data class Order(
        val search: List<String> = emptyList(),
        val isbn: List<String> = emptyList(),
    )
}

data class ProviderProperties(
    val enabled: Boolean = false,
    val baseUrl: String,
    val connectTimeout: Duration = Duration.ofSeconds(1),
    val readTimeout: Duration = Duration.ofSeconds(3),
    /** Requests per second on the wire; `null` for a provider limited by a daily budget instead. */
    val ratePerSecond: Int? = null,
    val apiKey: String? = null,
    val country: String? = null,
)
