package com.sauerwein.booktracker.adapter.bookdata.resilience

/**
 * Base for every provider's own failure type, so the resilience layer can tell a
 * source failing from a bug without knowing any provider by name.
 */
abstract class BookSourceException(message: String) : RuntimeException(message)
