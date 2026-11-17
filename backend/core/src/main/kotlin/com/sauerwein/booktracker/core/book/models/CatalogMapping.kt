package com.sauerwein.booktracker.core.book.models

import java.time.Instant

/**
 * The licence rule, in one place: only a record its source may store publicly, and only one
 * with a stable `(source, sourceId)` identity — without it the catalog row could never be
 * deduped or refreshed. The database allowlist backstops the first half.
 */
val BookCandidate.isCatalogEligible: Boolean
    get() = persistableInPublicCatalog && sourceId != null

/**
 * Whether a hit not yet in the catalog may be offered for adding: eligible, and an ISBN so the
 * stored row can be matched to a later scan (D22). Adding resolves the ISBN first (9.9 Q10) and
 * falls back to the hit's own full record (D29).
 */
val BookCandidate.isAddable: Boolean
    get() = isCatalogEligible && isbn != null

/**
 * How the app can add this result, or `null` if it cannot: the one rule, for every source.
 * A stored book by its id. Every other hit by its ISBN, which adding resolves like a scan, so a
 * row looks the same however it was added (9.9 Q10) and a hit whose data may not be stored never
 * enters the catalog itself (9.7 D1, D6). An eligible hit also carries its own record, the
 * fallback when its ISBN resolves to nothing (D29).
 */
val BookCandidate.addRef: CatalogBookRef?
    get() = when {
        bookId != null -> CatalogBookRef.Stored(bookId)
        isAddable -> CatalogBookRef.ByIsbn(isbn!!, record = SourceRef(source, sourceId!!))
        !persistableInPublicCatalog && isbn != null -> CatalogBookRef.ByIsbn(isbn)
        else -> null
    }

/**
 * A hit whose data may not be stored, and which has no ISBN to resolve, can be neither added
 * nor copied into a private book (9.7 D6, D8): it is not shown at all.
 */
val BookCandidate.isDeadEnd: Boolean
    get() = bookId == null && !persistableInPublicCatalog && isbn == null

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
        language = language,
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
    language = language,
    source = source,
    sourceId = sourceId,
    workKey = workKey,
    persistableInPublicCatalog = ownerId == null,
    bookId = id,
    kind = kind,
)
