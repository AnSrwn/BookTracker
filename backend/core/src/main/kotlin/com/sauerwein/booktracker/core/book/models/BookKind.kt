package com.sauerwein.booktracker.core.book.models

/**
 * Whether a book is shared by everyone or belongs to one user. Derived from the owner, never
 * stored on its own: a catalog row has no owner, a private row has exactly one (ADR-006).
 */
enum class BookKind { CATALOG, PRIVATE }

val Book.kind: BookKind
    get() = if (ownerId == null) BookKind.CATALOG else BookKind.PRIVATE
