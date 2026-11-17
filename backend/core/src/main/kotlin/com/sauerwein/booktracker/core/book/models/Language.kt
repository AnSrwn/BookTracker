package com.sauerwein.booktracker.core.book.models

import com.sauerwein.booktracker.core.shared.exception.ValidationException
import java.util.IllformedLocaleException
import java.util.Locale

private const val VALIDATION_EXCEPTION_FIELD = "language"
private const val VALIDATION_EXCEPTION_CODE = "InvalidLanguage"

private val SUBTAG = Regex("[a-z]{2,3}")

/** Not a language: undetermined, multiple, no linguistic content, uncoded (ISO 639-2). */
private val NOT_A_LANGUAGE = setOf("und", "mul", "zxx", "mis")

/** Reserved for local use (ISO 639-2 `qaa`–`qtz`): means nothing outside the system that assigned it. */
private val LOCAL_USE = "qaa".."qtz"

/**
 * ISO 639-2 has two three-letter codes for twenty languages: the bibliographic (B) one that
 * libraries use — DNB and Open Library send `ger` — and the terminology (T) one that
 * `java.util.Locale` knows (`deu`). B → T, so both resolve to the same two-letter code.
 */
private val BIBLIOGRAPHIC_TO_TERMINOLOGY = mapOf(
    "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "chi" to "zho",
    "cze" to "ces", "dut" to "nld", "fre" to "fra", "geo" to "kat", "ger" to "deu",
    "gre" to "ell", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa",
    "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
)

private val TWO_LETTER: Set<String> = Locale.getISOLanguages().toSet()

private val THREE_TO_TWO_LETTER: Map<String, String> = TWO_LETTER
    .mapNotNull { code -> runCatching { Locale.of(code).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { it to code } }
    .toMap()

/**
 * A book's language as a BCP 47 primary language subtag: the two-letter ISO 639-1 code where
 * one exists (`de`, `en`), else the three-letter code — what Android's and iOS' locales use, so
 * the app can name it in the user's language (9.9 Q9).
 *
 * The one normalisation for every input: a full tag keeps only its language (`de-AT` → `de`), and
 * the ISO 639-2 codes libraries send map to the two-letter code (`ger`, `deu` → `de`). A source and
 * a user therefore can never store the same language in two spellings.
 */
@JvmInline
value class Language private constructor(val value: String) {
    override fun toString() = value

    companion object {
        fun of(raw: String): Language {
            val tag = raw.trim().replace('_', '-')
            // The whole tag must be well-formed, even though only its language is kept: `de-!!` is no tag
            try {
                Locale.Builder().setLanguageTag(tag)
            } catch (_: IllformedLocaleException) {
                invalid("Language must be a BCP 47 language tag like 'de': '$raw'")
            }
            val subtag = tag.lowercase().substringBefore('-')
            if (!SUBTAG.matches(subtag)) invalid("Language must be a BCP 47 language tag like 'de': '$raw'")

            return when (subtag.length) {
                2 -> subtag.takeIf { it in TWO_LETTER } ?: invalid("Unknown language code: '$raw'")
                else -> {
                    if (subtag in NOT_A_LANGUAGE || subtag in LOCAL_USE) invalid("Not a language: '$raw'")
                    val terminology = BIBLIOGRAPHIC_TO_TERMINOLOGY[subtag] ?: subtag
                    THREE_TO_TWO_LETTER[terminology] ?: terminology
                }
            }.let(::Language)
        }

        /** For source data: a code we cannot read is no language rather than a failed record. */
        fun ofOrNull(raw: String?): Language? = try {
            raw?.let(::of)
        } catch (_: ValidationException) {
            null
        }

        private fun invalid(message: String): Nothing =
            throw ValidationException(field = VALIDATION_EXCEPTION_FIELD, code = VALIDATION_EXCEPTION_CODE, message = message)
    }
}
