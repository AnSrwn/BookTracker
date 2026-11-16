package com.sauerwein.booktracker.core.book.models

/** What a user can add to their books: a stored catalog row, or a source record not stored yet. */
sealed interface CatalogBookRef {
    data class Stored(val id: BookId) : CatalogBookRef

    /** A search hit, as carried by its signed hit id (D28). Stored from the full record on add (D29). */
    data class SourceRecord(val source: SourceId, val recordId: SourceRecordId) : CatalogBookRef
}
