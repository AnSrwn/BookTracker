package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.SourceId

data class SourceDescriptor(
    val id: SourceId,
    val supportsIsbnLookup: Boolean,
    val supportsFreeTextSearch: Boolean,
    val providesCovers: Boolean,
    val persistableInPublicCatalog: Boolean,
)
