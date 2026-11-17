package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId

/**
 * ISBN registration groups that get their own lookup order. The group stands in for the
 * edition's language until 9.9 adds field precedence keyed by language. An ISBN in no group
 * here uses the default order.
 */
enum class IsbnGroup(private val prefix: String) {

    /**
     * German-language ISBNs: `978-3`, the registration group of the German-language area —
     * Germany, Austria and Switzerland, not Germany alone.
     *
     * Why they get their own order: by legal deposit, the Deutsche Nationalbibliothek receives
     * essentially every German-language printing, so its record is the authority for these
     * editions — Open Library and Google index only the printings they were fed (architecture §7).
     */
    GERMAN_LANGUAGE("9783");

    fun contains(isbn: Isbn): Boolean = isbn.value.startsWith(prefix)

    companion object {
        /** `null` for an ISBN that belongs to no group with its own order. */
        fun of(isbn: Isbn): IsbnGroup? = entries.firstOrNull { it.contains(isbn) }
    }
}

/**
 * Source ids in the order an ISBN lookup asks them: one list per [IsbnGroup], and [default]
 * for an ISBN in no group or in a group without its own list.
 */
data class IsbnOrder(
    val default: List<SourceId>,
    val byGroup: Map<IsbnGroup, List<SourceId>> = emptyMap(),
) {
    fun forGroup(group: IsbnGroup?): List<SourceId> = group?.let(byGroup::get) ?: default

    /** Every configured list, for the startup checks. */
    val all: Map<String, List<SourceId>>
        get() = mapOf(DEFAULT_NAME to default) + byGroup.mapKeys { (group, _) -> group.name.lowercase() }

    companion object {
        const val DEFAULT_NAME = "default"
    }
}
