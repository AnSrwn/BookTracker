package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val PREFIX = "h2"
private const val SEPARATOR = '.'
private const val FIELD_SEPARATOR = ':'
private const val KIND_ISBN = "i"
private const val ALGORITHM = "HmacSHA256"
private const val TAG_BYTES = 16
private const val MIN_SECRET_BYTES = 32

/**
 * Opaque ids for search hits that are not stored yet (D28). The id carries what the hit is —
 * its ISBN, and for a hit that may enter the catalog by itself also its own source record as the
 * fallback (9.9 Q10, Q13) — and a signature over it, so the app can hand it back but cannot forge
 * or alter it. Opaque, not secret: the payload is only base64, so whoever decodes it can read the
 * provider. The app must not, and never needs to — it hands the id back as it is. No server state:
 * any instance with the same secret can read any id.
 *
 * Format: `h2.<base64url payload>.<base64url tag>`, payload `i:<isbn13>` or
 * `i:<isbn13>:<source>:<record>`. The kind is part of the signed payload. Ids of the former
 * record kind (`r:`, before 9.9) no longer decode: no app held them, and they lived only as long
 * as a search result.
 */
class CatalogHitIds(secret: ByteArray) {

    private val key = run {
        require(secret.size >= MIN_SECRET_BYTES) { "Hit id secret must have at least $MIN_SECRET_BYTES bytes" }
        SecretKeySpec(secret.copyOf(), ALGORITHM)
    }

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(ref: CatalogBookRef.ByIsbn): String =
        sign(listOfNotNull(KIND_ISBN, ref.isbn.value, ref.record?.source?.value, ref.record?.recordId?.value))

    /** `null` for anything that is not an untampered id signed with this secret. */
    fun decode(id: String): CatalogBookRef? {
        val parts = id.split(SEPARATOR)
        if (parts.size != 3 || parts[0] != PREFIX) return null

        val payload = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return null
        val tag = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(tag, tag(payload))) return null

        // A record id may itself contain the separator: it is the last field and takes the rest
        val fields = String(payload, Charsets.UTF_8).split(FIELD_SEPARATOR, limit = 4)
        return runCatching {
            when {
                fields[0] != KIND_ISBN -> null
                fields.size == 2 -> CatalogBookRef.ByIsbn(Isbn.of(fields[1]))
                fields.size == 4 -> CatalogBookRef.ByIsbn(
                    isbn = Isbn.of(fields[1]),
                    record = SourceRef(SourceId.of(fields[2]), SourceRecordId(fields[3])),
                )
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
