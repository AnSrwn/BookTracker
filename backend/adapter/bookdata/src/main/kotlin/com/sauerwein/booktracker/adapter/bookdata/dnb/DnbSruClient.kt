package com.sauerwein.booktracker.adapter.bookdata.dnb

import com.sauerwein.booktracker.core.book.BookMetadataSource
import com.sauerwein.booktracker.core.book.models.*
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse

/** `recordPacking=xml` pinned: DNB's default today, and string-packed records would not parse. */
private const val SRU_PATH =
    "/sru/dnb?version=1.1&operation=searchRetrieve&query={query}&recordSchema=MARC21-xml&recordPacking=xml&maximumRecords={max}"

/**
 * One ISBN can match a few records (reprints, a print and an e-book edition); the first one that
 * maps wins. Small on purpose: SRU is for targeted lookups, bulk belongs on OAI-PMH (ADR-007).
 */
private const val ISBN_LOOKUP_RECORDS = 5

private val XML_TYPES = listOf(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType("application", "*+xml"))

/**
 * The Deutsche Nationalbibliothek over SRU, MARC21-XML. An ISBN lookup and field authority,
 * never a search provider: SRU cannot rank by relevance (architecture §7). Free, no key, no
 * registration, CC0. Throttling sits in the RestClient, circuit breaking and metrics in the
 * ResilientBookMetadataSource around it — as for Open Library.
 */
class DnbSruClient(private val restClient: RestClient) : BookMetadataSource {

    companion object {
        val DESCRIPTOR = SourceDescriptor(
            id = SourceId.of("dnb"),
            supportsIsbnLookup = true,
            supportsFreeTextSearch = false,
            providesCovers = false,
            persistableInPublicCatalog = true,
        )
    }

    override val descriptor = DESCRIPTOR

    private val mapper = DnbMapper(descriptor)

    /** Never called: the descriptor declares no free-text search, and the composite reads it. */
    override fun search(text: String): List<BookCandidate> = emptyList()

    override fun findByIsbn(isbn: Isbn): BookCandidate? =
        query("isbn=${isbn.value}", ISBN_LOOKUP_RECORDS).firstNotNullOfOrNull {
            mapper.toCandidate(
                it,
                requestedIsbn = isbn
            )
        }

    /** A record by its IDN. The id came back from our app: only a well-formed one goes into a query. */
    override fun findByRecordId(ref: SourceRef): BookCandidate? {
        if (ref.source != descriptor.id) return null
        val idn = idnOrNull(ref.recordId.value) ?: return null

        return query("idn=${idn.value}", 1)
            .firstNotNullOfOrNull { mapper.toCandidate(it, requestedIsbn = null) }
            ?.takeIf { it.sourceId == idn }
    }

    private fun query(cql: String, maximumRecords: Int): List<MarcRecord> =
        restClient.get()
            .uri(SRU_PATH, cql, maximumRecords)
            .exchange { _, response -> response.readRecords() }

    private fun ConvertibleClientHttpResponse.readRecords(): List<MarcRecord> {
        if (!statusCode.is2xxSuccessful) {
            throw DnbException("DNB answered ${statusCode.value()}")
        }

        val contentType = headers.contentType
        if (contentType == null || XML_TYPES.none { it.isCompatibleWith(contentType) }) {
            throw DnbException("DNB answered with content type $contentType instead of XML")
        }

        return SruMarcXml.parse(body)
    }
}
