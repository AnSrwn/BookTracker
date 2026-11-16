package com.sauerwein.booktracker.core.book

import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val PREFIX = "h1"
private const val SEPARATOR = '.'
private const val PAYLOAD_SEPARATOR = ':'
private const val ALGORITHM = "HmacSHA256"
private const val TAG_BYTES = 16
private const val MIN_SECRET_BYTES = 32

/**
 * Opaque ids for search hits that are not stored yet (D28). The id carries which source record
 * the hit is and a signature over it, so the app can hand it back but cannot forge or alter it —
 * and it never learns which provider is behind it. No server state: any instance with the same
 * secret can read any id. Format: `h1.<base64url payload>.<base64url tag>`.
 */
class CatalogHitIds(secret: ByteArray) {

    private val key = run {
        require(secret.size >= MIN_SECRET_BYTES) { "Hit id secret must have at least $MIN_SECRET_BYTES bytes" }
        SecretKeySpec(secret.copyOf(), ALGORITHM)
    }

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(source: SourceId, recordId: SourceRecordId): String {
        val payload = "${source.value}$PAYLOAD_SEPARATOR${recordId.value}".toByteArray(Charsets.UTF_8)
        return listOf(PREFIX, encoder.encodeToString(payload), encoder.encodeToString(tag(payload)))
            .joinToString(SEPARATOR.toString())
    }

    /** `null` for anything that is not an untampered id signed with this secret. */
    fun decode(id: String): CatalogBookRef.SourceRecord? {
        val parts = id.split(SEPARATOR)
        if (parts.size != 3 || parts[0] != PREFIX) return null

        val payload = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return null
        val tag = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(tag, tag(payload))) return null

        val (source, recordId) = String(payload, Charsets.UTF_8).split(PAYLOAD_SEPARATOR, limit = 2)
            .takeIf { it.size == 2 } ?: return null
        return runCatching { CatalogBookRef.SourceRecord(SourceId.of(source), SourceRecordId(recordId)) }.getOrNull()
    }

    private fun tag(payload: ByteArray): ByteArray =
        Mac.getInstance(ALGORITHM).apply { init(key) }.doFinal(payload).copyOf(TAG_BYTES)
}
