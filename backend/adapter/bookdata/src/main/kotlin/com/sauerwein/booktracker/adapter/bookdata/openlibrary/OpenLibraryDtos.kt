package com.sauerwein.booktracker.adapter.bookdata.openlibrary

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Only the fields the mapper reads, all nullable: Open Library records are community-edited,
 * and any of them may be missing. Unknown properties are ignored (Jackson 3 default).
 */
data class OpenLibraryEdition(
    val key: String? = null,
    val title: String? = null,
    val authors: List<OpenLibraryKeyRef>? = null,
    val publishers: List<String>? = null,
    @JsonProperty("publish_date") val publishDate: String? = null,
    @JsonProperty("number_of_pages") val numberOfPages: Int? = null,
    @JsonProperty("isbn_13") val isbn13: List<String>? = null,
    @JsonProperty("isbn_10") val isbn10: List<String>? = null,
    val covers: List<Long>? = null,
    val works: List<OpenLibraryKeyRef>? = null,
    /** `/languages/ger`: MARC codes, i.e. ISO 639-2/B. */
    val languages: List<OpenLibraryKeyRef>? = null,
)

data class OpenLibraryKeyRef(val key: String? = null)

data class OpenLibraryAuthor(val name: String? = null)

data class OpenLibrarySearchResponse(val docs: List<OpenLibrarySearchDoc>? = null)

data class OpenLibrarySearchDoc(
    /** The work key, `/works/OL…W` — search hits are works. */
    val key: String? = null,
    val title: String? = null,
    @JsonProperty("author_name") val authorName: List<String>? = null,
    @JsonProperty("author_key") val authorKey: List<String>? = null,
    @JsonProperty("first_publish_year") val firstPublishYear: Int? = null,
    @JsonProperty("cover_i") val coverId: Long? = null,
    /**
     * The one edition of this work that best matches the query, requested via
     * `fields=editions,editions.key,…`. Search hits become edition-level through it: its key and
     * ISBNs belong together, unlike the work-level `isbn` list, which mixes every edition.
     */
    val editions: OpenLibrarySearchEditions? = null,
)

data class OpenLibrarySearchEditions(val docs: List<OpenLibrarySearchEdition>? = null)

data class OpenLibrarySearchEdition(
    /** `/books/OL…M` */
    val key: String? = null,
    val title: String? = null,
    /** ISBN-10 and ISBN-13 mixed, as Open Library lists them. */
    val isbn: List<String>? = null,
    @JsonProperty("publish_year") val publishYear: List<Int>? = null,
    @JsonProperty("cover_i") val coverId: Long? = null,
    val publisher: List<String>? = null,
    /**
     * This edition's languages as MARC codes (`ger`). Not the work-level `language`, which lists
     * every translation of the work (Eragon: `fre, spa, ita, …`).
     */
    val language: List<String>? = null,
)
