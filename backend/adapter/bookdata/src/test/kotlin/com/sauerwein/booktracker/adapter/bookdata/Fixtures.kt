package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef

fun openLibraryRef(id: String) = SourceRef(SourceId.of("open_library"), SourceRecordId(id))
