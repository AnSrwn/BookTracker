package com.sauerwein.booktracker.core.book.exception

import com.sauerwein.booktracker.core.book.models.SourceId

/** A metadata source could not answer — unlike a miss, which is `null` or an empty list. */
class SourceUnavailableException(val source: SourceId, cause: Throwable? = null) :
    RuntimeException("Book source $source is unavailable", cause)
