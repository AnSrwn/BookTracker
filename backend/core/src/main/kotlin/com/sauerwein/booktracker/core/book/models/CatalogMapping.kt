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
 * stored row can be matched to a later scan (D22). Adding fetches the full record first (D29).
 */
val BookCandidate.isAddable: Boolean
    get() = isCatalogEligible && isbn != null

/**
 * How the app can add this result, or `null` if it cannot: the one rule, for every source.
 * A stored book by its id; an eligible hit by its source record (D29); a hit whose data may not
 * be stored by its ISBN alone, which adding resolves like a scan (9.7 D1, D6).
 */
val BookCandidate.addRef: CatalogBookRef?
    get() = when {
        bookId != null -> CatalogBookRef.Stored(bookId)
        isAddable -> CatalogBookRef.SourceRecord(source, sourceId!!)
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
    bookId = id,
    kind = kind,
)
