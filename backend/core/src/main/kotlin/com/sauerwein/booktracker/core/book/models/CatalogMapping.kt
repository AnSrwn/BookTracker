package com.sauerwein.booktracker.core.book.models

import java.time.Instant

/**
 * The licence rule, in one place: only a record its source may store publicly, and only one
 * with a stable `(source, sourceId)` identity — without it the catalog row could never be
 * deduped or refreshed. The database allowlist backstops the first half.
 */
val BookCandidate.isCatalogEligible: Boolean
    get() = persistableInPublicCatalog && sourceId != null

fun BookCandidate.toCatalogBook(id: BookId, now: Instant): Book {
    require(isCatalogEligible) { "Candidate from $source may not enter the public catalog" }

    return Book(
        id = id,
        ownerId = null,
        title = title,
        authors = authors,
        publisher = publisher,
        isbn = isbn,
        publicationYear = publicationYear,
        pageCount = pageCount,
        coverUrl = coverUrl,
        source = source,
        sourceId = sourceId,
        sourceFetchedAt = now,
        workKey = workKey,
        createdAt = now,
        updatedAt = now,
    )
}

/** A catalog row may be stored publicly by definition; a private book never may. */
fun Book.toCandidate() = BookCandidate(
    title = title,
    authors = authors,
    publisher = publisher,
    isbn = isbn,
    publicationYear = publicationYear,
    pageCount = pageCount,
    coverUrl = coverUrl,
    source = source,
    sourceId = sourceId,
    workKey = workKey,
    persistableInPublicCatalog = ownerId == null,
)
