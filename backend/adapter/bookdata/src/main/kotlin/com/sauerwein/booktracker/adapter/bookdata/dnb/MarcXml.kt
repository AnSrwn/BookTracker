// The http:// strings here are XML namespace and parser feature names, compared as exact text and never
// fetched; https:// would match nothing. The requests themselves go to https://services.dnb.de.
@file:Suppress("HttpUrlsUsage")

package com.sauerwein.booktracker.adapter.bookdata.dnb

import com.sauerwein.booktracker.adapter.bookdata.resilience.BookSourceException
import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.IOException
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

private const val SRU_NAMESPACE = "http://www.loc.gov/zing/srw/"
private const val MARC_NAMESPACE = "http://www.loc.gov/MARC21/slim"
private const val BIBLIOGRAPHIC = "Bibliographic"
private const val MARC21_SCHEMA = "MARC21-xml"

class DnbException(message: String, cause: Throwable? = null) : BookSourceException(message) {
    init {
        cause?.let(::initCause)
    }
}

data class MarcSubfield(val code: Char, val value: String)

data class MarcDataField(
    val tag: String,
    val ind1: Char,
    val ind2: Char,
    val subfields: List<MarcSubfield>,
) {
    fun values(code: Char): List<String> = subfields.filter { it.code == code }.map { it.value }
    fun first(code: Char): String? = values(code).firstOrNull()
}

/**
 * A MARC21 record as DNB sends it, raw: cleaning and interpretation are the mapper's job.
 * Control fields can repeat (`007` once per physical component), so every occurrence is kept, in order.
 */
data class MarcRecord(
    val leader: String,
    val controlFields: Map<String, List<String>>,
    val dataFields: List<MarcDataField>,
) {
    /** The first occurrence — for `007`, the one describing the main item. */
    fun control(tag: String): String? = controlFields[tag]?.firstOrNull()
    fun fields(tag: String): List<MarcDataField> = dataFields.filter { it.tag == tag }
}

/**
 * Reads an SRU `searchRetrieve` response with `recordSchema=MARC21-xml`. Third-party XML, so the
 * parser is locked down: no DOCTYPE at all, no external entities, DTDs or schemas, no XInclude
 * (XXE). A document that tries any of it fails like any malformed answer — as a [DnbException].
 *
 * Only an answer of the expected shape can be a miss: a `searchRetrieveResponse` with a record
 * count, every record a MARC record. Anything else — a maintenance page served as XML, records
 * packed as escaped text, a per-record diagnostic — is a failure, never "no records" (R13).
 */
object SruMarcXml {

    fun parse(body: InputStream): List<MarcRecord> {
        val document = try {
            hardenedFactory().newDocumentBuilder()
                .apply { setErrorHandler(ThrowingErrorHandler) }
                .parse(body)
        } catch (e: SAXException) {
            throw DnbException("DNB answered with XML that could not be read: ${e.message}", e)
        } catch (e: IOException) {
            throw DnbException("DNB answer could not be read: ${e.message}", e)
        }

        val root = document.documentElement
        if (root.namespaceURI != SRU_NAMESPACE || root.localName != "searchRetrieveResponse") {
            throw DnbException("DNB answered with <${root.tagName}> instead of an SRU searchRetrieveResponse")
        }

        // A query DNB rejects still answers 200, with the reason in <diagnostics>
        root.sruChildren("diagnostics").firstOrNull()?.let { diagnostics ->
            throw DnbException("DNB rejected the query: ${diagnostics.textContent.trim().replace(Regex("\\s+"), " ")}")
        }

        root.sruChildren("numberOfRecords").firstOrNull()?.textContent?.trim()?.toIntOrNull()
            ?: throw DnbException("DNB answered without a record count")

        return root.sruChildren("records").flatMap { it.sruChildren("record") }
            .map(::marcRecordOf)
            .filter { it.getAttribute("type").let { type -> type.isEmpty() || type == BIBLIOGRAPHIC } }
            .map(::toRecord)
    }

    /**
     * The MARC record inside one SRU record. A string-packed record or a surrogate diagnostic
     * ("record not available in this schema") has none — that is a failure, not a skipped record.
     */
    private fun marcRecordOf(sruRecord: Element): Element {
        val recordData = sruRecord.sruChildren("recordData").singleOrNull()
            ?: throw DnbException("DNB answered with an SRU record without recordData")
        val schema = sruRecord.sruChildren("recordSchema").firstOrNull()?.textContent?.trim()
        if (schema != null && schema != MARC21_SCHEMA) {
            throw DnbException("DNB answered with a record in schema '$schema' instead of $MARC21_SCHEMA")
        }

        return recordData.childElements().singleOrNull { it.namespaceURI == MARC_NAMESPACE && it.localName == "record" }
            ?: throw DnbException("DNB answered with an SRU record that holds no MARC21-XML record")
    }

    private fun toRecord(record: Element) = MarcRecord(
        leader = record.children("leader").firstOrNull()?.textContent.orEmpty(),
        controlFields = record.children("controlfield")
            .groupBy(keySelector = { it.getAttribute("tag") }, valueTransform = { it.textContent }),
        dataFields = record.children("datafield").map { field ->
            MarcDataField(
                tag = field.getAttribute("tag"),
                ind1 = field.getAttribute("ind1").firstOrNull() ?: ' ',
                ind2 = field.getAttribute("ind2").firstOrNull() ?: ' ',
                subfields = field.children("subfield").mapNotNull { subfield ->
                    subfield.getAttribute("code").firstOrNull()?.let { MarcSubfield(it, subfield.textContent) }
                },
            )
        },
    )

    private fun Element.children(localName: String): List<Element> =
        childElements().filter { it.namespaceURI == MARC_NAMESPACE && it.localName == localName }

    private fun Element.sruChildren(localName: String): List<Element> =
        childElements().filter { it.namespaceURI == SRU_NAMESPACE && it.localName == localName }

    private fun Element.childElements(): List<Element> = childNodes.elements()

    private fun org.w3c.dom.NodeList.elements(): List<Element> =
        (0 until length).map(::item).filterIsInstance<Element>()

    /** Created per call: a factory is cheap, and not guaranteed to be thread-safe. */
    private fun hardenedFactory(): DocumentBuilderFactory = try {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
    } catch (e: ParserConfigurationException) {
        // The JDK parser supports all of these; a missing one must stop us, never be skipped
        throw IllegalStateException("XML parser cannot be hardened against XXE", e)
    }

    /** The default handler prints `[Fatal Error]` to stderr before throwing; this only throws. */
    private object ThrowingErrorHandler : ErrorHandler {
        override fun warning(exception: SAXParseException) {}
        override fun error(exception: SAXParseException): Unit = throw exception
        override fun fatalError(exception: SAXParseException): Unit = throw exception
    }
}
