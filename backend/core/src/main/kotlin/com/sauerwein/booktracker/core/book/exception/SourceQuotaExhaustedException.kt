package com.sauerwein.booktracker.core.book.exception

import com.sauerwein.booktracker.core.book.models.SourceId

/**
 * The source's budget is spent (a daily quota), so it was not asked. Unlike an outage this
 * says nothing about the book: a composite skips the source instead of counting it as failed
 * (D4). On its own it is still an unavailable source, i.e. a 503.
 */
class SourceQuotaExhaustedException(source: SourceId, cause: Throwable? = null) :
    SourceUnavailableException(source, cause)
