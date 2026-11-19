package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.shared.exception.ValidationException
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * One uniform block per provider, so adding one is filling in a block (docs/book-data.md).
 * Each provider is switched on by its own `enabled` (`@ConditionalOnProperty` on its config);
 * a block that is missing or disabled leaves no trace in the running application.
 */
@ConfigurationProperties("booktracker.bookdata")
data class BookDataProperties(
    /** Sent to every provider. Open Library asks for a contact address in it. */
    val userAgent: String,
    val order: Order = Order(),
    val precedence: PrecedenceProperties = PrecedenceProperties(),
    val openLibrary: ProviderProperties? = null,
    val googleBooks: ProviderProperties? = null,
    val dnb: ProviderProperties? = null,
) {
    /**
     * Source ids in the order their search hits are listed. Providers that are not listed come
     * after the listed ones, by id; listed ids that are not running are ignored. Which value wins
     * a field is [precedence], not this.
     */
    data class Order(val search: List<String> = emptyList())

    /** Bound form of [MergePrecedence]; see there for how a field's order is looked up. */
    data class PrecedenceProperties(
        /** Who decides the book's language. Absent or empty uses `default.fields`. */
        val language: List<String> = emptyList(),
        val default: FieldPrecedenceProperties = FieldPrecedenceProperties(),
        /** Keyed by language (`de`); a key is normalised like any language (`ger` → `de`). */
        val byLanguage: Map<String, FieldPrecedenceProperties> = emptyMap(),
    ) {
        fun toMergePrecedence(): MergePrecedence {
            val blocks = byLanguage.entries.associate { (key, block) ->
                val language = try {
                    Language.of(key)
                } catch (e: ValidationException) {
                    throw IllegalStateException("booktracker.bookdata.precedence.by-language.$key is not a language", e)
                }
                language to block.toFieldPrecedence()
            }
            check(blocks.size == byLanguage.size) {
                "booktracker.bookdata.precedence.by-language names one language twice: ${byLanguage.keys}"
            }
            return MergePrecedence(
                language = language.map(SourceId::of),
                default = default.toFieldPrecedence(),
                byLanguage = blocks,
            )
        }
    }

    /**
     * One list for every field, plus optional lists for single fields. An empty list means
     * "no list of its own", like an absent one — never "sorted by id".
     */
    data class FieldPrecedenceProperties(
        val fields: List<String> = emptyList(),
        val title: List<String>? = null,
        val authors: List<String>? = null,
        val publisher: List<String>? = null,
        val publicationYear: List<String>? = null,
        val pageCount: List<String>? = null,
        val cover: List<String>? = null,
        val workKey: List<String>? = null,
    ) {
        fun toFieldPrecedence() = FieldPrecedence(
            fields = fields.map(SourceId::of),
            overrides = mapOf(
                MergeField.TITLE to title,
                MergeField.AUTHORS to authors,
                MergeField.PUBLISHER to publisher,
                MergeField.PUBLICATION_YEAR to publicationYear,
                MergeField.PAGE_COUNT to pageCount,
                MergeField.COVER to cover,
                MergeField.WORK_KEY to workKey,
            ).mapNotNull { (field, order) -> order?.takeIf { it.isNotEmpty() }?.let { field to it.map(SourceId::of) } }.toMap(),
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
