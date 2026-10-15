package com.sauerwein.booktracker.core.book.models

private const val MAX_LENGTH = 32
private val PATTERN = Regex("[a-z][a-z0-9_]*")

@JvmInline
value class SourceId private constructor(val value: String) {
    override fun toString() = value

    companion object {
        val MANUAL = of("manual")

        fun of(raw: String): SourceId {
            require(raw.isNotBlank()) { "Source id must not be blank" }
            require(raw.length <= MAX_LENGTH) { "Source id must be at most $MAX_LENGTH characters: '$raw'" }
            require(PATTERN.matches(raw)) { "Source id must match [a-z][a-z0-9_]*, got '$raw'" }

            return SourceId(raw)
        }
    }
}
