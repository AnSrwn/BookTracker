package com.sauerwein.booktracker.core.book.models

/**
 * An author as a source credits it. `ref` is the issuing source's own key for the person
 * (`OL1425963A`, a GND id) — kept so authors can be grouped later, not used as an identity now.
 */
data class Author(
    val name: String,
    val ref: SourceRef?,
)
