package com.sauerwein.booktracker.adapter.bookdata.dnb

import com.sauerwein.booktracker.adapter.bookdata.FakeHttp
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A real `searchRetrieve` answer from DNB (2026-11-17): `query=isbn=9783570402931`. */
internal fun eragonRecords(): List<MarcRecord> = parse(FakeHttp.fixture("dnb-sru-isbn.xml"))

/** 10 real records for `tit=Tschick and per=Herrndorf`: print, e-book, audiobook, translations. */
internal fun tschickRecords(): List<MarcRecord> = parse(FakeHttp.fixture("dnb-sru-tschick.xml"))

internal fun parse(xml: String): List<MarcRecord> = SruMarcXml.parse(xml.byteInputStream())

internal fun sruResponse(vararg marcRecords: String): String =
    """<?xml version="1.0" encoding="UTF-8"?>
    <searchRetrieveResponse xmlns="http://www.loc.gov/zing/srw/"><version>1.1</version>
    <numberOfRecords>${marcRecords.size}</numberOfRecords><records>${
        marcRecords.joinToString("") {
            "<record><recordSchema>MARC21-xml</recordSchema><recordPacking>xml</recordPacking><recordData>$it</recordData></record>"
        }
    }</records></searchRetrieveResponse>"""

class SruMarcXmlTest {

    @Test
    fun `reads leader, control fields, indicators and subfields`() {
        val record = eragonRecords().single()

        assertEquals("00000pam a2200000 cc4500", record.leader)
        assertEquals("1055026991", record.control("001"))
        val imprint = record.fields("264").single()
        assertEquals('1', imprint.ind2)
        assertEquals("cbj", imprint.first('b'))
        assertEquals(listOf("9783570402931", "3570402932"), record.fields("020").flatMap { it.values('a') })
    }

    @Test
    fun `reads every record of a response`() {
        assertEquals(10, tschickRecords().size)
    }

    @Test
    fun `an answer without records is an empty list`() {
        assertTrue(parse(sruResponse()).isEmpty())
    }

    @Test
    fun `skips records that are not bibliographic`() {
        val authority = """<record xmlns="http://www.loc.gov/MARC21/slim" type="Authority"><leader>00000nz  a2200000nc 4500</leader></record>"""

        assertTrue(parse(sruResponse(authority)).isEmpty())
    }

    /** A rejected query still answers 200 — the reason is in `<diagnostics>`. Shape as DNB sent it (2026-11-17). */
    @Test
    fun `turns an SRU diagnostic into a failure`() {
        val diagnostic = """<?xml version="1.0" encoding="UTF-8"?>
            <searchRetrieveResponse xmlns="http://www.loc.gov/zing/srw/"><version>1.1</version><diagnostics>
            <diag:diagnostic xmlns:diag="http://www.loc.gov/zing/srw/diagnostic/"><diag:uri>info:srw/diagnostic/1/10</diag:uri>
            <diag:details>Query syntax error</diag:details><diag:message>expected index or term, got '='</diag:message>
            </diag:diagnostic></diagnostics></searchRetrieveResponse>"""

        val failure = assertFailsWith<DnbException> { parse(diagnostic) }
        assertTrue("Query syntax error" in failure.message.orEmpty())
    }

    /** F3: `007` repeats once per physical component; the first one describes the main item. */
    @Test
    fun `keeps every occurrence of a repeated control field, in order`() {
        val audiobook = tschickRecords().single { it.control("001") == "1388606216" }

        assertEquals(2, audiobook.controlFields.getValue("007").size)
        assertTrue(audiobook.control("007")!!.startsWith("s"))
    }

    // Only an answer of the expected shape can be a miss (review F1, R13)

    @Test
    fun `an XML answer that is not an SRU response is a failure`() {
        val failure = assertFailsWith<DnbException> { parse("""<?xml version="1.0"?><html><body>Wartungsarbeiten</body></html>""") }
        assertTrue("searchRetrieveResponse" in failure.message.orEmpty())
    }

    @Test
    fun `an SRU response without a record count is a failure`() {
        assertFailsWith<DnbException> {
            parse("""<searchRetrieveResponse xmlns="http://www.loc.gov/zing/srw/"><version>1.1</version></searchRetrieveResponse>""")
        }
    }

    @Test
    fun `a string-packed record is a failure, not a skipped record`() {
        val stringPacked = """<searchRetrieveResponse xmlns="http://www.loc.gov/zing/srw/"><version>1.1</version>
            <numberOfRecords>1</numberOfRecords><records><record><recordSchema>MARC21-xml</recordSchema>
            <recordPacking>string</recordPacking><recordData>&lt;record xmlns="http://www.loc.gov/MARC21/slim"&gt;&lt;/record&gt;</recordData>
            </record></records></searchRetrieveResponse>"""

        assertFailsWith<DnbException> { parse(stringPacked) }
    }

    @Test
    fun `a surrogate diagnostic in place of a record is a failure`() {
        val surrogate = """<searchRetrieveResponse xmlns="http://www.loc.gov/zing/srw/"><version>1.1</version>
            <numberOfRecords>1</numberOfRecords><records><record><recordSchema>info:srw/schema/1/diagnostics-v1.1</recordSchema>
            <recordPacking>xml</recordPacking><recordData><diagnostic xmlns="http://www.loc.gov/zing/srw/diagnostic/">
            <uri>info:srw/diagnostic/1/67</uri><message>Record not available in this schema</message></diagnostic></recordData>
            </record></records></searchRetrieveResponse>"""

        val failure = assertFailsWith<DnbException> { parse(surrogate) }
        assertTrue("diagnostics-v1.1" in failure.message.orEmpty())
    }

    @Test
    fun `malformed XML is a failure, not an empty answer`() {
        assertFailsWith<DnbException> { parse("<searchRetrieveResponse><records>") }
    }

    /** R5: third-party XML must not make us read a local file (XXE). */
    @Test
    fun `refuses a document type declaration and reads no external entity`() {
        val secret = Files.createTempFile("dnb-xxe", ".txt")
        try {
            secret.writeText("TOP-SECRET-CONTENT")
            val attack = """<?xml version="1.0"?>
                <!DOCTYPE searchRetrieveResponse [<!ENTITY xxe SYSTEM "${secret.toUri()}">]>
                <searchRetrieveResponse xmlns="http://www.loc.gov/zing/srw/"><records><record><recordData>
                <record xmlns="http://www.loc.gov/MARC21/slim"><leader>&xxe;</leader></record>
                </recordData></record></records></searchRetrieveResponse>"""

            val failure = assertFailsWith<DnbException> { parse(attack) }
            assertFalse("TOP-SECRET-CONTENT" in failure.message.orEmpty())
            assertTrue("DOCTYPE" in failure.message.orEmpty())
        } finally {
            secret.deleteIfExists()
        }
    }
}
