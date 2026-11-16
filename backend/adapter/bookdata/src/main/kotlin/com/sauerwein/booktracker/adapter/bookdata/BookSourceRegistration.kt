package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.BookMetadataSource

/**
 * How a provider joins the composite. Providers are registered as this, not as
 * [BookMetadataSource] beans, so the composite is the only [BookMetadataSource] in the context:
 * no `@Primary`, and the composite cannot end up injecting itself.
 */
class BookSourceRegistration(val source: BookMetadataSource)
