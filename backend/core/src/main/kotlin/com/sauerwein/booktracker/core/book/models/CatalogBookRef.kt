package com.sauerwein.booktracker.core.book.models

/** What a user can add to their books: a stored catalog row, or a source hit not stored yet. */
sealed interface CatalogBookRef {
    data class Stored(val id: BookId) : CatalogBookRef

    /** A search hit, as carried by its signed hit id (D28). Stored from the full record on add (D29). */
    data class SourceRecord(val source: SourceId, val recordId: SourceRecordId) : CatalogBookRef

    /**
     * A hit whose own data may not be stored (9.7 D1, D6): only its ISBN is kept. Adding it
     * resolves the ISBN like a scan, so what enters the catalog is a CC0 record, never the hit.
     */
    data class ByIsbn(val isbn: Isbn) : CatalogBookRef
}
