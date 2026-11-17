package com.sauerwein.booktracker.adapter.bookdata.dnb

import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import com.sauerwein.booktracker.core.book.models.SourceRef
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val DNB = SourceId.of("dnb")
private val ERAGON_ISBN = Isbn.of("9783570402931")

class DnbMapperTest {

    private val mapper = DnbMapper(DnbSruClient.DESCRIPTOR)

    private val eragon = eragonRecords().single()

    private fun tschick(idn: String) = tschickRecords().single { it.control("001") == idn }

    private fun MarcRecord.mapped(isbn: Isbn? = null) = mapper.toCandidate(this, requestedIsbn = isbn)

    /** The record with field [tag] replaced by [fields] (none: removed). */
    private fun MarcRecord.with(tag: String, vararg fields: MarcDataField) =
        copy(dataFields = dataFields.filterNot { it.tag == tag } + fields)

    private fun field(tag: String, vararg subfields: Pair<Char, String>, ind2: Char = ' ') =
        MarcDataField(tag, ' ', ind2, subfields.map { (code, value) -> MarcSubfield(code, value) })

    private fun BookCandidate.strings(): List<String> =
        listOfNotNull(title, publisher, sourceId?.value) + authors.flatMap { listOfNotNull(it.name, it.ref?.recordId?.value) }

    // Roadmap §9.8 "Done when" (E1)

    @Test
    fun `maps the Eragon record as the roadmap expects`() {
        val candidate = assertNotNull(eragon.mapped(ERAGON_ISBN))

        assertEquals("Eragon – Das Vermächtnis der Drachenreiter", candidate.title)
        assertEquals(listOf(Author("Christopher Paolini", SourceRef(DNB, SourceRecordId("129384240")))), candidate.authors)
        assertEquals("cbj", candidate.publisher)
        assertEquals(2014, candidate.publicationYear)
        assertEquals(733, candidate.pageCount)
        assertEquals(ERAGON_ISBN, candidate.isbn)
        assertEquals(DNB, candidate.source)
        assertEquals(SourceRecordId("1055026991"), candidate.sourceId)
        assertNull(candidate.coverUrl)
        assertNull(candidate.workKey)
        assertTrue(candidate.persistableInPublicCatalog)
    }

    @Test
    fun `leaves no control character in any field`() {
        val candidates = (eragonRecords() + tschickRecords()).mapNotNull { it.mapped() }

        assertTrue(candidates.size > 5)
        candidates.flatMap { it.strings() }.forEach { value ->
            assertTrue(value.none { it.isISOControl() }, "Control character in '$value'")
        }
    }

    /** DNB sends umlauts decomposed (`a` + U+0308); the catalog must hold the composed form. */
    @Test
    fun `composes decomposed umlauts`() {
        val candidates = (eragonRecords() + tschickRecords()).mapNotNull { it.mapped() }

        candidates.flatMap { it.strings() }.forEach { value ->
            assertTrue(Normalizer.isNormalized(value, Normalizer.Form.NFC), "Not NFC: '$value'")
        }
        assertTrue("̈" in eragon.fields("245").single().values('p').single(), "fixture should be decomposed")
    }

    /** Review F7: invisible format characters break equality; ZWNJ is part of Persian spelling. */
    @Test
    fun `drops invisible format characters but keeps the joiners scripts need`() {
        assertEquals("Tschick", text("Tsch\u00ADick\u200B\uFEFF"))
        assertEquals("Tschick", text("\u200ETschick\u200F"))
        assertEquals("می\u200Cخواهم", text("می\u200Cخواهم"))
        assertEquals("a\u200Db", text("a\u200Db"))
        // The joiners must survive, not just be absent on both sides
        assertTrue('\u200C' in text("می\u200Cخواهم"), "ZWNJ was dropped")
        assertTrue('\u200D' in text("a\u200Db"), "ZWJ was dropped")
    }

    // ISBN (R7, I3)

    @Test
    fun `answers a lookup with exactly the asked ISBN, also when the record lists it as ISBN-10`() {
        assertEquals(ERAGON_ISBN, eragon.mapped(Isbn.of("3570402932"))?.isbn)
    }

    /** E2: `$z` is cancelled or invalid — a scanned barcode must never get a book through it. */
    @Test
    fun `does not answer an ISBN the record only carries as cancelled`() {
        val cancelledOnly = eragon.with("020", field("020", 'z' to "9783570402931"))

        assertNull(cancelledOnly.mapped(ERAGON_ISBN))
    }

    @Test
    fun `does not answer an ISBN the record does not carry`() {
        assertNull(eragon.mapped(Isbn.of("9783641023027")))
    }

    @Test
    fun `takes the first valid ISBN when no ISBN was asked`() {
        val invalidFirst = eragon.with("020", field("020", 'a' to "123"), field("020", 'a' to "3570402932 (kart.)"))

        assertEquals(ERAGON_ISBN, invalidFirst.mapped()?.isbn)
    }

    // Print only (R10, ADR-008)

    /** E3: *Tschick – Hörspiel*, leader `i`, `Online-Ressource`. */
    @Test
    fun `drops an audiobook`() {
        assertNull(tschick("1388606216").mapped(Isbn.of("9783732490813")))
    }

    /** E4: leader `a`, but `007 cr` and `Online-Ressource, 112 Seiten`. */
    @Test
    fun `drops an e-book`() {
        assertNull(tschick("1296687937").mapped(Isbn.of("9783963082085")))
    }

    @Test
    fun `keeps the print edition of the same book`() {
        assertEquals(112, tschick("1293401269").mapped(Isbn.of("9783963082023"))?.pageCount)
    }

    @Test
    fun `drops an online carrier even when 007 and 300 do not say so`() {
        val online = eragon.copy(controlFields = eragon.controlFields - "007")
            .with("338", field("338", 'a' to "Online-Ressource", 'b' to "cr"))

        assertNull(online.mapped(ERAGON_ISBN))
    }

    /** Review F3: a printed book with an accompanying CD-ROM has `007 tu` and then `007 co`. */
    @Test
    fun `keeps a print book with an accompanying CD-ROM`() {
        val withCdRom = eragon.copy(controlFields = eragon.controlFields + ("007" to listOf("tu", "co")))

        assertNotNull(withCdRom.mapped(ERAGON_ISBN))
    }

    @Test
    fun `drops a record whose main item is electronic`() {
        val electronic = eragon.copy(controlFields = eragon.controlFields + ("007" to listOf("cr||||||||||||", "tu")))

        assertNull(electronic.mapped(ERAGON_ISBN))
    }

    @Test
    fun `keeps a print record without 337 and 338, as many foreign records are`() {
        assertNotNull(eragon.with("337").with("338").mapped(ERAGON_ISBN))
    }

    // Authors (R9, I7, I8)

    /** E5: translator, narrators and contributors are not authors. */
    @Test
    fun `takes only authors, not translators or contributors`() {
        assertEquals(listOf("Wolfgang Herrndorf"), tschick("1338432095").mapped()?.authors?.map { it.name })
        assertEquals(listOf("Wolfgang Herrndorf"), tschick("1374532762").mapped()?.authors?.map { it.name })
    }

    @Test
    fun `takes a co-author from 700`() {
        assertEquals(
            listOf("Wolfgang Herrndorf", "Angelika Lundquist-Mog"),
            tschick("127575306X").mapped()?.authors?.map { it.name },
        )
    }

    @Test
    fun `keeps a main entry without any role code`() {
        val noRole = eragon.with("100", field("100", 'a' to "Paolini, Christopher"))

        assertEquals(listOf(Author("Christopher Paolini", null)), noRole.mapped(ERAGON_ISBN)?.authors)
    }

    @Test
    fun `strips non-sorting markers from names`() {
        val withParticle = eragon.with("100", field("100", 'a' to "Jascheroff, Constantin \u0098von\u009C", '4' to "aut"))

        assertEquals("Constantin von Jascheroff", withParticle.mapped(ERAGON_ISBN)?.authors?.single()?.name)
    }

    @Test
    fun `yields nothing for a record without an author`() {
        assertNull(tschick("138436708X").mapped(Isbn.of("9789941377150")))
    }

    // Title (R17, Q2) — E13

    @Test
    fun `puts a real part number after the main title`() {
        val numbered = eragon.with(
            "245",
            field("245", 'a' to "Eragon", 'n' to "1", 'p' to "\u0098Das\u009C Vermächtnis der Drachenreiter", 'c' to "Christopher Paolini"),
        )

        assertEquals("Eragon 1 – Das Vermächtnis der Drachenreiter", numbered.mapped(ERAGON_ISBN)?.title)
    }

    /** Review F4: each part number stays with its part. */
    @Test
    fun `keeps several part numbers with their parts`() {
        val nested = eragon.with(
            "245",
            field("245", 'a' to "Gesammelte Werke", 'n' to "2", 'p' to "Romane", 'n' to "1", 'p' to "Tschick"),
        )

        assertEquals("Gesammelte Werke 2 – Romane 1 – Tschick", nested.mapped(ERAGON_ISBN)?.title)
    }

    @Test
    fun `keeps a title DNB wrote entirely into 245a`() {
        val inA = eragon.with("245", field("245", 'a' to "\u0098Die\u009C Tribute von Panem 2. Gefährliche Liebe", 'b' to "Roman"))

        assertEquals("Die Tribute von Panem 2. Gefährliche Liebe", inA.mapped(ERAGON_ISBN)?.title)
    }

    @Test
    fun `trims ISBD punctuation from the end of a subfield`() {
        val punctuated = eragon.with("245", field("245", 'a' to "Eragon :", 'b' to "Roman /"))

        assertEquals("Eragon", punctuated.mapped(ERAGON_ISBN)?.title)
    }

    // Imprint and extent (I6)

    @Test
    fun `reads the year from the forms DNB uses`() {
        fun year(c: String) = eragon.with("264", field("264", 'b' to "cbj", 'c' to c, ind2 = '1')).mapped(ERAGON_ISBN)?.publicationYear

        assertEquals(2023, year("Dezember 2023"))
        assertEquals(2019, year("[2019?]"))
        assertEquals(2026, year("2026"))
        assertNull(year("[ca. 20. Jh.]"))
    }

    @Test
    fun `reads the page count from the forms DNB uses`() {
        fun pages(a: String) = eragon.with("300", field("300", 'a' to a)).mapped(ERAGON_ISBN)?.pageCount

        assertEquals(127, pages("127 Seiten"))
        assertEquals(345, pages("XII, 345 S."))
        assertNull(pages("2 Bände"))
        assertNull(pages("0 S."))
    }

    @Test
    fun `takes the publisher from the publication statement, not from production or distribution`() {
        val imprints = eragon.with(
            "264",
            field("264", 'b' to "Druckerei", ind2 = '3'),
            field("264", 'b' to "cbj", ind2 = '1'),
            field("264", 'b' to "cbj Kinderbuch", ind2 = '1'),
        )

        assertEquals("cbj", imprints.mapped(ERAGON_ISBN)?.publisher)
    }

    @Test
    fun `falls back to 260 in older records`() {
        val older = eragon.with("264", field("260", 'a' to "München", 'b' to "cbj", 'c' to "2004"))

        assertEquals("cbj", older.mapped(ERAGON_ISBN)?.publisher)
        assertEquals(2004, older.mapped(ERAGON_ISBN)?.publicationYear)
    }

    @Test
    fun `yields nothing without an IDN`() {
        assertNull(eragon.copy(controlFields = eragon.controlFields - "001").mapped(ERAGON_ISBN))
    }
}
