package com.sauerwein.booktracker.adapter.bookdata.dnb

import com.sauerwein.booktracker.core.book.models.*
import java.text.Normalizer

/** Leader position 06, type of record: `a` is language material, i.e. text. */
private const val LEADER_TYPE_POSITION = 6
private const val TYPE_LANGUAGE_MATERIAL = 'a'

/** `007` position 00 `c`: electronic resource. */
private const val ELECTRONIC_RESOURCE = 'c'

/** `338$b` carrier type `cr`: online resource. */
private const val ONLINE_CARRIER = "cr"
private const val ONLINE_RESOURCE_EXTENT = "Online-Ressource"

/** `008` positions 35–37: the language of the item, a MARC (ISO 639-2/B) code. */
private val FIXED_FIELD_LANGUAGE = 35..37

/** `$4` relator code for an author. Others: `trl` translator, `nrt` narrator, `ctb` contributor… */
private const val AUTHOR_ROLE = "aut"

/** What DNB writes into `245$n` when a part has no number. It is not part of the title. */
private val PART_NUMBER_UNKNOWN = setOf("[...]", "[…]")

/** Between the main title (with its part number) and the part title: *Eragon 1 – Das Vermächtnis…* (Q2). */
private const val PART_TITLE_SEPARATOR = " – "

private val IDN = Regex("[0-9]{8,9}[0-9X]")
private val GND_ID = Regex("""\(DE-588\)([0-9X-]+)""")
private val ISBN_TOKEN = Regex("^[0-9Xx-]+")
private val YEAR = Regex("""(?<!\d)\d{4}(?!\d)""")

/** `733 S.`, `127 Seiten`, `XII, 345 S.` — the arabic number before the page abbreviation. */
private val PAGES = Regex("""(?<!\d)(\d+)\s*(?:S\.|Seiten)""")

/** ISBD punctuation that MARC leaves at the end of a subfield (`Eragon :`, `cbj ;`). */
private val TRAILING_PUNCTUATION = Regex("""[\s:/;=,]+$""")
private val WHITESPACE = Regex("""\s+""")

/** Zero-width non-joiner and joiner: format characters, but part of the spelling in Persian and others. */
private val KEPT_JOINERS = setOf('\u200C', '\u200D')

/**
 * A DNB record id (IDN), e.g. `1055026991` or `138436708X`. A record id handed back by our own app
 * is still validated before it goes into a query.
 */
internal fun idnOrNull(value: String): SourceRecordId? = value.takeIf(IDN::matches)?.let(::SourceRecordId)

/**
 * MARC21 → [BookCandidate], by hand for the six fields we need (architecture §7). Returns `null`
 * for a record we must not use: not print (ADR-008), no title, no IDN, or — on an ISBN lookup — not
 * carrying the asked ISBN as a valid `020$a`. A record without an author is kept with none (9.10 Q9):
 * the Duden, the Bible, an anonymous epic. Editors and issuing bodies are never made authors (Q11).
 */
class DnbMapper(private val descriptor: SourceDescriptor) {

    /**
     * [requestedIsbn] is the ISBN of a lookup. The record must carry it as a valid `020$a`
     * (`$z` is cancelled or invalid), and the candidate carries exactly it: a scanned barcode
     * never gets another book, and the catalog row matches the next scan of the same barcode.
     */
    fun toCandidate(record: MarcRecord, requestedIsbn: Isbn?): BookCandidate? {
        if (!isPrint(record)) return null

        val isbns = record.fields("020").flatMap { it.values('a') }
            .mapNotNull { raw -> ISBN_TOKEN.find(raw.trim())?.value?.let(Isbn::ofOrNull) }
        val isbn = if (requestedIsbn != null) {
            requestedIsbn.takeIf { it in isbns } ?: return null
        } else {
            isbns.firstOrNull()
        }

        val sourceId = record.control("001")?.let(::text)?.let(::idnOrNull) ?: return null
        val title = title(record) ?: return null
        val authors = authors(record)
        val imprint = record.fields("264").firstOrNull { it.ind2 == '1' } ?: record.fields("260").firstOrNull()

        return BookCandidate(
            title = title,
            authors = authors,
            publisher = imprint?.values('b')?.map(::text)?.firstOrNull { it.isNotEmpty() },
            isbn = isbn,
            publicationYear = imprint?.values('c')?.firstNotNullOfOrNull { YEAR.find(it)?.value?.toInt() },
            pageCount = record.fields("300").flatMap { it.values('a') }
                .firstNotNullOfOrNull { PAGES.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?.takeIf { it > 0 },
            coverUrl = null,
            language = language(record),
            source = descriptor.id,
            sourceId = sourceId,
            workKey = null,
            persistableInPublicCatalog = descriptor.persistableInPublicCatalog,
        )
    }

    /**
     * Print only (ADR-008): an app about pages read has no use for an e-book or an audiobook.
     * Read from the coded fields, not only `300$a` — many foreign print records have no `338`,
     * so "requires `338 nc`" would drop real books.
     */
    private fun isPrint(record: MarcRecord): Boolean {
        if (record.leader.getOrNull(LEADER_TYPE_POSITION) != TYPE_LANGUAGE_MATERIAL) return false
        // The first `007` describes the main item; a later one may be an accompanying CD-ROM (`tu`, then `co`)
        if (record.control("007")?.firstOrNull() == ELECTRONIC_RESOURCE) return false
        if (record.fields("338").any { field -> field.values('b').any { it.trim() == ONLINE_CARRIER } }) return false
        return record.fields("300").none { field -> field.values('a').any { ONLINE_RESOURCE_EXTENT in it } }
    }

    /**
     * `041$a`, the language code field, else `008/35-37`. With several (a bilingual edition, a
     * translation listing its original in `$h`), the first `$a` — the text's own language — counts.
     */
    private fun language(record: MarcRecord): Language? =
        record.fields("041").flatMap { it.values('a') }.firstNotNullOfOrNull { Language.ofOrNull(text(it)) }
            ?: record.control("008")?.takeIf { it.length > FIXED_FIELD_LANGUAGE.last }
                ?.substring(FIXED_FIELD_LANGUAGE)?.let(Language::ofOrNull)

    /**
     * `$a $n – $p` (Q2), walked in subfield order so each part number stays with its part:
     * every `$n` (unless it is DNB's "no number" placeholder) follows with a space, every `$p`
     * with ` – `. `$a Gesammelte Werke $n 2 $p Romane $n 1 $p Tschick` → *Gesammelte Werke 2 –
     * Romane 1 – Tschick*.
     */
    private fun title(record: MarcRecord): String? {
        val field = record.fields("245").firstOrNull() ?: return null
        val main = field.first('a')?.let(::text)?.takeIf { it.isNotEmpty() } ?: return null

        return buildString {
            append(main)
            for (subfield in field.subfields) {
                val value = text(subfield.value)
                when {
                    value.isEmpty() -> Unit
                    subfield.code == 'n' && value !in PART_NUMBER_UNKNOWN -> append(' ').append(value)
                    subfield.code == 'p' -> append(PART_TITLE_SEPARATOR).append(value)
                }
            }
        }
    }

    /**
     * Authors only: `100` and `700` also list translators, narrators and editors, told apart by
     * `$4`. A `100` without any `$4` is kept — the main entry is the author by cataloguing rule.
     */
    private fun authors(record: MarcRecord): List<Author> {
        val main = record.fields("100").filter { roles(it).isEmpty() || AUTHOR_ROLE in roles(it) }
        val added = record.fields("700").filter { AUTHOR_ROLE in roles(it) }

        return (main + added).mapNotNull(::author).distinctBy { it.name }
    }

    private fun roles(field: MarcDataField): List<String> = field.values('4').map { it.trim() }

    private fun author(field: MarcDataField): Author? {
        val name = field.first('a')?.let(::text)?.let(::displayName)?.takeIf { it.isNotEmpty() } ?: return null
        val gndId = field.values('0').firstNotNullOfOrNull { GND_ID.matchEntire(it.trim())?.groupValues?.get(1) }

        return Author(name = name, ref = gndId?.let { SourceRef(descriptor.id, SourceRecordId(it)) })
    }

    /** `Paolini, Christopher` → `Christopher Paolini`. A name with no or several commas stays as it is. */
    private fun displayName(inverted: String): String {
        val parts = inverted.split(',')
        if (parts.size != 2) return inverted
        val (family, given) = parts.map { it.trim() }
        return if (given.isEmpty()) family else "$given $family"
    }
}

/**
 * Every string leaves here clean: DNB's non-sorting markers `\x98`/`\x9c` (`\x98Das\x9c
 * Vermächtnis…`) and every other control character removed, whitespace collapsed, and trailing
 * ISBD punctuation trimmed. Otherwise they land in the shared catalog and render as garbage.
 *
 * DNB also sends umlauts decomposed (NFD: `a` + U+0308). Composed to NFC, or a stored *Vermächtnis*
 * would never equal the same word typed by a user or sent by another source.
 *
 * Invisible format characters go too — zero-width space, BOM, soft hyphen, direction marks: they
 * render as nothing but break equality. Except ZWNJ/ZWJ, which change the spelling in Persian and
 * other scripts.
 */
internal fun text(raw: String): String =
    Normalizer.normalize(raw, Normalizer.Form.NFC)
        .map { if (it.isWhitespace()) ' ' else it }
        .filterNot { it.isISOControl() || (it.category == CharCategory.FORMAT && it !in KEPT_JOINERS) }
        .joinToString("")
        .replace(WHITESPACE, " ")
        .replace(TRAILING_PUNCTUATION, "")
        .trim()
