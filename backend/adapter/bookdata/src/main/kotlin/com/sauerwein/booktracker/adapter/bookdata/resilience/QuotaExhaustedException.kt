package com.sauerwein.booktracker.adapter.bookdata.resilience

/**
 * A provider's budget is spent (e.g. Google's daily quota). Deliberately not a
 * [BookSourceException]: it says nothing about the source's health, so the circuit breaker
 * ignores it, and the composite skips the provider instead of counting it as failed (D4).
 */
class QuotaExhaustedException(message: String) : RuntimeException(message)
