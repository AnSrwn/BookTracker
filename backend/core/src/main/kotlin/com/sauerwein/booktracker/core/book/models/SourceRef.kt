package com.sauerwein.booktracker.core.book.models

/**
 * A third-party key together with the source that issued it. Needed wherever the source
 * is not implied by the surrounding record: after a field-by-field merge an author may
 * come from a different source than the candidate it belongs to.
 */
data class SourceRef(
    val source: SourceId,
    val recordId: SourceRecordId,
)
