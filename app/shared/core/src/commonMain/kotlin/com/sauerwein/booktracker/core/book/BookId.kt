package com.sauerwein.booktracker.core.book

import kotlin.jvm.JvmInline

/** Id of a book as the backend assigns it. Opaque to the app: never parsed, only passed back. */
@JvmInline
value class BookId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "A book id must not be blank" }
    }
}
