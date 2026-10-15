package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId

fun testDescriptor(
    id: SourceId = SourceId.of("test_source"),
    supportsIsbnLookup: Boolean = true,
    supportsFreeTextSearch: Boolean = true,
    providesCovers: Boolean = true,
    persistableInPublicCatalog: Boolean = true,
) = SourceDescriptor(
    id = id,
    supportsIsbnLookup = supportsIsbnLookup,
    supportsFreeTextSearch = supportsFreeTextSearch,
    providesCovers = providesCovers,
    persistableInPublicCatalog = persistableInPublicCatalog,
)

fun testCandidate(
    title: String = "Dune",
    isbn: Isbn? = null,
    source: SourceId = SourceId.of("test_source"),
    sourceId: SourceRecordId? = SourceRecordId("test-record-1"),
    persistableInPublicCatalog: Boolean = true,
) = BookCandidate(
    title = title,
    author = "Frank Herbert",
    publisher = null,
    isbn = isbn,
    publicationYear = null,
    pageCount = null,
    coverUrl = null,
    source = source,
    sourceId = sourceId,
    persistableInPublicCatalog = persistableInPublicCatalog,
)
