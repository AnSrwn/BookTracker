package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.models.SourceId
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
    val dnb: ProviderProperties? = null,
) {
    /**
     * Source ids in the order their answers are used. Providers that are not listed come after
     * the listed ones, by id; listed ids that are not running are ignored. Until 9.9 merges
     * fields, the order only decides which hits come first and which lookup is asked first.
     */
    data class Order(
        val search: List<String> = emptyList(),
        val isbn: IsbnOrderProperties = IsbnOrderProperties(),
    )

    /** One ISBN order per [IsbnGroup]; see there for what each group is and why it has its own. */
    data class IsbnOrderProperties(
        /** For an ISBN in no group, and for a group without its own list. */
        val default: List<String> = emptyList(),
        /** For German-language ISBNs, `978-3` ([IsbnGroup.GERMAN_LANGUAGE]). Absent or empty uses [default]. */
        val germanLanguage: List<String>? = null,
    ) {
        /** An empty group list means "no order of its own", like an absent one — never "sorted by id". */
        fun toIsbnOrder() = IsbnOrder(
            default = default.map(SourceId::of),
            byGroup = listOfNotNull(
                germanLanguage?.takeIf { it.isNotEmpty() }?.let { IsbnGroup.GERMAN_LANGUAGE to it.map(SourceId::of) },
            ).toMap(),
        )
    }
}

data class ProviderProperties(
    /**
     * Not read from here: `@ConditionalOnProperty` on the provider's config reads the raw property
     * and decides whether the provider exists at all. Bound so a block is complete in one place;
     * the default matches the condition's — a missing `enabled` is off.
     */
    val enabled: Boolean = false,
    val baseUrl: String,
    val connectTimeout: Duration = Duration.ofSeconds(1),
    val readTimeout: Duration = Duration.ofSeconds(3),
    /** Requests per second on the wire; `null` for a provider limited by a daily budget instead. */
    val ratePerSecond: Int? = null,
    val apiKey: String? = null,
    val country: String? = null,
)
