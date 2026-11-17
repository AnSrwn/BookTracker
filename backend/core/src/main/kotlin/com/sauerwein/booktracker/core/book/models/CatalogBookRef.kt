package com.sauerwein.booktracker.core.book.models

/** What a user can add to their books: a stored catalog row, or a source hit not stored yet. */
sealed interface CatalogBookRef {
    data class Stored(val id: BookId) : CatalogBookRef

    /**
     * A search hit not stored yet, as carried by its signed hit id (D28). Adding resolves the ISBN
     * like a scan (9.9 Q10), so what enters the catalog is the merged CC0 record, never the hit —
     * for a hit whose data may not be stored (9.7 D1, D6) and for every other one alike.
     *
     * [record] is the hit's own source record, set only for a hit that may enter the catalog by
     * itself: if the ISBN resolves to nothing, that full record is stored instead (D29).
     */
    data class ByIsbn(val isbn: Isbn, val record: SourceRef? = null) : CatalogBookRef
}
