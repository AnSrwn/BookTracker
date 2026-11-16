package com.sauerwein.booktracker.core.book.models

@JvmInline
value class SourceRecordId(val value: String) {
    init {
        require(value.isNotBlank()) { "Source record id must not be blank" }
    }

    override fun toString() = value
}
