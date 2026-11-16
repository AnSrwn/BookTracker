package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val PREFIX = "h2"
private const val SEPARATOR = '.'
private const val FIELD_SEPARATOR = ':'
private const val KIND_RECORD = "r"
private const val KIND_ISBN = "i"
private const val ALGORITHM = "HmacSHA256"
private const val TAG_BYTES = 16
private const val MIN_SECRET_BYTES = 32

/**
 * Opaque ids for search hits that are not stored yet (D28). The id carries what the hit is —
 * a source record, or for a hit whose data may not be stored only its ISBN (9.7 D1) — and a
 * signature over it, so the app can hand it back but cannot forge or alter it, and never learns
 * which provider is behind it. No server state: any instance with the same secret can read any id.
 *
 * Format: `h2.<base64url payload>.<base64url tag>`, payload `r:<source>:<record>` or `i:<isbn13>`.
 * The kind is part of the signed payload, so one kind can never be read as the other.
 */
class CatalogHitIds(secret: ByteArray) {

    private val key = run {
        require(secret.size >= MIN_SECRET_BYTES) { "Hit id secret must have at least $MIN_SECRET_BYTES bytes" }
        SecretKeySpec(secret.copyOf(), ALGORITHM)
    }

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(ref: CatalogBookRef.SourceRecord): String =
        sign(listOf(KIND_RECORD, ref.source.value, ref.recordId.value))

    fun encode(ref: CatalogBookRef.ByIsbn): String =
        sign(listOf(KIND_ISBN, ref.isbn.value))

    /** `null` for anything that is not an untampered id signed with this secret. */
    fun decode(id: String): CatalogBookRef? {
        val parts = id.split(SEPARATOR)
        if (parts.size != 3 || parts[0] != PREFIX) return null

        val payload = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return null
        val tag = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(tag, tag(payload))) return null

        val fields = String(payload, Charsets.UTF_8).split(FIELD_SEPARATOR, limit = 3)
        return runCatching {
            when {
                fields.size == 3 && fields[0] == KIND_RECORD ->
                    CatalogBookRef.SourceRecord(SourceId.of(fields[1]), SourceRecordId(fields[2]))
                fields.size == 2 && fields[0] == KIND_ISBN -> CatalogBookRef.ByIsbn(Isbn.of(fields[1]))
                else -> null
            }
        }.getOrNull()
    }

    private fun sign(fields: List<String>): String {
        val payload = fields.joinToString(FIELD_SEPARATOR.toString()).toByteArray(Charsets.UTF_8)
        return listOf(PREFIX, encoder.encodeToString(payload), encoder.encodeToString(tag(payload)))
            .joinToString(SEPARATOR.toString())
    }

    private fun tag(payload: ByteArray): ByteArray =
        Mac.getInstance(ALGORITHM).apply { init(key) }.doFinal(payload).copyOf(TAG_BYTES)
}
