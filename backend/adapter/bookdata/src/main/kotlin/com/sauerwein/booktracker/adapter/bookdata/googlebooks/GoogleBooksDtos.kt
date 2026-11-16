package com.sauerwein.booktracker.adapter.bookdata.googlebooks

/**
 * Only the fields the mapper reads (the request asks for exactly these with `fields=`), all
 * nullable. Unknown properties are ignored (Jackson 3 default).
 */
data class GoogleVolumesResponse(val items: List<GoogleVolume>? = null)

data class GoogleVolume(
    val id: String? = null,
    val volumeInfo: GoogleVolumeInfo? = null,
)

data class GoogleVolumeInfo(
    val title: String? = null,
    val authors: List<String>? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    /** `0` means unknown, not zero pages: publisher records often carry it. */
    val pageCount: Int? = null,
    /** Library-scan records often carry only `OTHER` identifiers and no ISBN at all. */
    val industryIdentifiers: List<GoogleIndustryIdentifier>? = null,
    val imageLinks: GoogleImageLinks? = null,
)

data class GoogleIndustryIdentifier(
    val type: String? = null,
    val identifier: String? = null,
)

data class GoogleImageLinks(val thumbnail: String? = null)

/** Google's error body, read only to tell a spent quota from other refusals. */
data class GoogleErrorResponse(val error: GoogleError? = null)

data class GoogleError(
    val message: String? = null,
    val status: String? = null,
    val errors: List<GoogleErrorDetail>? = null,
)

data class GoogleErrorDetail(val reason: String? = null)
