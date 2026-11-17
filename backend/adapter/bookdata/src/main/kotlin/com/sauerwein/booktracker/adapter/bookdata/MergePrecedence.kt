package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.SourceId

/** The fields of a candidate that a merge takes from one provider or another. */
enum class MergeField {
    TITLE, AUTHORS, PUBLISHER, PUBLICATION_YEAR, PAGE_COUNT, COVER, WORK_KEY;

    /** As written in `application.yaml`. */
    val configName: String get() = name.lowercase().replace('_', '-')
}

/**
 * Which provider's value wins a field: one list for every field, and optionally a list of its own
 * for single fields. A field without its own list uses [fields].
 */
data class FieldPrecedence(
    val fields: List<SourceId> = emptyList(),
    val overrides: Map<MergeField, List<SourceId>> = emptyMap(),
)

/**
 * First non-null wins over these orders (ADR-007), keyed by the book's language where it matters
 * (9.9 Q3): covers genuinely differ by language, and DNB is the authority for German-language
 * editions (legal deposit). The language itself is decided first, over [language], which is the
 * same for every book — otherwise the language would depend on the order it picks.
 *
 * A field's order is looked up as: the language's own list for the field, the language's
 * `fields`, then the same two in [default]. A language without a block of its own uses [default].
 */
data class MergePrecedence(
    val language: List<SourceId> = emptyList(),
    val default: FieldPrecedence = FieldPrecedence(),
    val byLanguage: Map<Language, FieldPrecedence> = emptyMap(),
) {
    fun forLanguage(): List<SourceId> = language.ifEmpty { default.fields }

    fun forField(field: MergeField, language: Language?): List<SourceId> {
        val block = language?.let(byLanguage::get)
        return block?.overrides?.get(field)
            ?: block?.fields?.takeIf { it.isNotEmpty() }
            ?: default.overrides[field]
            ?: default.fields
    }

    /** Every configured list by its config path, for the startup checks and log. */
    val all: Map<String, List<SourceId>>
        get() = buildMap {
            put("language", language)
            putBlock("default", default)
            byLanguage.forEach { (language, block) -> putBlock("by-language.$language", block) }
        }

    private fun MutableMap<String, List<SourceId>>.putBlock(path: String, block: FieldPrecedence) {
        put("$path.fields", block.fields)
        block.overrides.forEach { (field, order) -> put("$path.${field.configName}", order) }
    }
}
