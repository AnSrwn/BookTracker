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
)

data class OpenLibraryKeyRef(val key: String? = null)

data class OpenLibraryAuthor(val name: String? = null)

data class OpenLibrarySearchResponse(val docs: List<OpenLibrarySearchDoc>? = null)

data class OpenLibrarySearchDoc(
    val title: String? = null,
    @JsonProperty("author_name") val authorName: List<String>? = null,
    @JsonProperty("first_publish_year") val firstPublishYear: Int? = null,
    @JsonProperty("cover_i") val coverId: Long? = null,
    @JsonProperty("cover_edition_key") val coverEditionKey: String? = null,
)
