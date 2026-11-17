package com.sauerwein.booktracker.adapter.bookdata

import com.sauerwein.booktracker.core.book.models.Author
import com.sauerwein.booktracker.core.book.models.BookCandidate
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.models.Language
import com.sauerwein.booktracker.core.book.models.SourceId
import com.sauerwein.booktracker.core.book.models.SourceRecordId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private val ISBN = Isbn.of("9783570402931")
private val AUTHORITY = SourceId.of("authority")
private val COMMUNITY = SourceId.of("community")
private val FINDER = SourceId.of("finder")
private val GERMAN = Language.of("de")

/** Made-up provider ids: the merge must work for any provider, never a named one. */
class BookCandidateMergeTest {

    private fun candidate(
        source: SourceId,
        title: String = "Eragon ($source)",
        authors: List<String> = listOf("Christopher Paolini"),
        publisher: String? = null,
        pageCount: Int? = null,
        coverUrl: String? = null,
        language: Language? = null,
        workKey: String? = null,
        persistable: Boolean = true,
        authorsFromWork: Boolean = false,
    ) = BookCandidate(
        title = title,
        authors = authors.map { Author(it, ref = null) },
        publisher = publisher,
        isbn = ISBN,
        publicationYear = null,
        pageCount = pageCount,
        coverUrl = coverUrl,
        language = language,
        source = source,
        sourceId = SourceRecordId("$source-1"),
        workKey = workKey?.let(::SourceRecordId),
        persistableInPublicCatalog = persistable,
        authorsFromWork = authorsFromWork,
    )

    /** As shipped, in made-up ids: the authority leads German editions, the finder everything else. */
    private val shipped = MergePrecedence(
        language = listOf(AUTHORITY, COMMUNITY, FINDER),
        default = FieldPrecedence(
            fields = listOf(FINDER, COMMUNITY, AUTHORITY),
            overrides = mapOf(MergeField.COVER to listOf(COMMUNITY, FINDER)),
        ),
        byLanguage = mapOf(
            GERMAN to FieldPrecedence(
                fields = listOf(AUTHORITY, FINDER, COMMUNITY),
                overrides = mapOf(MergeField.COVER to listOf(FINDER, COMMUNITY)),
            ),
        ),
    )

    private val merge = BookCandidateMerge(shipped)

    /** A1: the German scan — the authority's text and identity, the community's cover. */
    @Test
    fun `takes each field from the first provider in the language's order that has it`() {
        val authority = candidate(AUTHORITY, title = "Eragon – Das Vermächtnis der Drachenreiter", publisher = "cbj", pageCount = 733, language = GERMAN)
        val community = candidate(COMMUNITY, title = "Eragon", pageCount = 700, coverUrl = "https://community/c.jpg", workKey = "OL5819895W")

        val merged = merge.merge(listOf(community, authority), ISBN)

        assertEquals("Eragon – Das Vermächtnis der Drachenreiter", merged.title)
        assertEquals("cbj", merged.publisher)
        assertEquals(733, merged.pageCount)
        assertEquals("https://community/c.jpg", merged.coverUrl)
        assertEquals(SourceRecordId("OL5819895W"), merged.workKey)
        assertEquals(GERMAN, merged.language)
        assertEquals(AUTHORITY, merged.source)
        assertEquals(SourceRecordId("authority-1"), merged.sourceId)
    }

    /** A2: reordering the configuration changes whose title — and so whose identity — wins. */
    @Test
    fun `reordering the language's list changes whose title and identity win`() {
        val swapped = BookCandidateMerge(
            shipped.copy(byLanguage = mapOf(GERMAN to FieldPrecedence(fields = listOf(COMMUNITY, AUTHORITY)))),
        )
        val authority = candidate(AUTHORITY, language = GERMAN)
        val community = candidate(COMMUNITY)

        val merged = swapped.merge(listOf(authority, community), ISBN)

        assertEquals("Eragon (community)", merged.title)
        assertEquals(COMMUNITY, merged.source)
    }

    @Test
    fun `a book whose language has no block of its own uses the default order`() {
        val merged = merge.merge(listOf(candidate(AUTHORITY, language = Language.of("en")), candidate(COMMUNITY)), ISBN)

        assertEquals("Eragon (community)", merged.title)
        assertEquals(Language.of("en"), merged.language)
    }

    @Test
    fun `a book of unknown language uses the default order`() {
        val merged = merge.merge(listOf(candidate(AUTHORITY), candidate(COMMUNITY)), ISBN)

        assertEquals("Eragon (community)", merged.title)
        assertNull(merged.language)
    }

    /** The language is decided over its own list first, so it never depends on the order it picks. */
    @Test
    fun `the language comes from its own list, not from the field order`() {
        val merged = merge.merge(
            listOf(candidate(FINDER, language = Language.of("en")), candidate(AUTHORITY, language = GERMAN)),
            ISBN,
        )

        assertEquals(GERMAN, merged.language)
        assertEquals("Eragon (authority)", merged.title)
    }

    /** I1: two author lists are never combined — the same person spelled twice would appear twice. */
    @Test
    fun `takes the first author list whole`() {
        val merged = merge.merge(
            listOf(
                candidate(FINDER, authors = listOf("Paolini, Christopher")),
                candidate(COMMUNITY, authors = listOf("Christopher Paolini", "Someone Else")),
            ),
            ISBN,
        )

        assertEquals(listOf("Paolini, Christopher"), merged.authors.map { it.name })
    }

    /** 9.10 Q9: some books have no author (the Duden); the merge does not invent one. */
    @Test
    fun `a book nobody names an author for has none`() {
        val merged = merge.merge(listOf(candidate(AUTHORITY, authors = emptyList()), candidate(COMMUNITY, authors = emptyList())), ISBN)

        assertEquals(emptyList(), merged.authors)
    }

    @Test
    fun `an empty author list does not hide a later provider's authors`() {
        val merged = merge.merge(
            listOf(candidate(AUTHORITY, authors = emptyList(), language = GERMAN), candidate(COMMUNITY, authors = listOf("Wolfgang Herrndorf"))),
            ISBN,
        )

        assertEquals(listOf("Wolfgang Herrndorf"), merged.authors.map { it.name })
    }

    /**
     * R16, A14: the Nibelungenlied. The authority rightly names nobody; the community's work credits
     * two translators. A record of the edition itself beats the work, even without an author.
     */
    @Test
    fun `work authors lose to a provider's own record of the edition, even one without authors`() {
        val merged = merge.merge(
            listOf(
                candidate(AUTHORITY, authors = emptyList(), language = GERMAN),
                candidate(COMMUNITY, authors = listOf("Siegfried Grosse", "Helmut Brackert"), authorsFromWork = true, coverUrl = "https://c/1.jpg"),
            ),
            ISBN,
        )

        assertEquals(emptyList(), merged.authors)
        assertEquals(false, merged.authorsFromWork)
        // The record still contributes everything else
        assertEquals("https://c/1.jpg", merged.coverUrl)
    }

    /** A15 */
    @Test
    fun `work authors lose to another provider's authors even when ranked first`() {
        val merged = merge.merge(
            listOf(
                candidate(FINDER, authors = listOf("Work Author"), authorsFromWork = true),
                candidate(AUTHORITY, authors = listOf("Christopher Paolini")),
            ),
            ISBN,
        )

        assertEquals(listOf("Christopher Paolini"), merged.authors.map { it.name })
    }

    /** A1: *Pride and Prejudice* — only the community knows it, and only through the work. */
    @Test
    fun `work authors count when no other provider has the edition`() {
        val merged = merge.merge(listOf(candidate(COMMUNITY, authors = listOf("Jane Austen"), authorsFromWork = true)), ISBN)

        assertEquals(listOf("Jane Austen"), merged.authors.map { it.name })
        assertEquals(true, merged.authorsFromWork)
    }

    /** A record whose authors came from the work may still name the row: only its authors rank last. */
    @Test
    fun `a record with work authors may still win the title`() {
        val merged = merge.merge(
            listOf(
                candidate(FINDER, title = "Pride and Prejudice", authors = listOf("Jane Austen"), authorsFromWork = true),
                candidate(AUTHORITY, title = "Stolz und Vorurteil", authors = emptyList()),
            ),
            ISBN,
        )

        assertEquals("Pride and Prejudice", merged.title)
        assertEquals(FINDER, merged.source)
        assertEquals(emptyList(), merged.authors)
    }

    /** ADR-007: a provider with nothing for a field contributes nothing; no rule asks who answered. */
    @Test
    fun `a field nobody knows stays empty`() {
        assertNull(merge.merge(listOf(candidate(AUTHORITY), candidate(COMMUNITY)), ISBN).publisher)
    }

    @Test
    fun `providers missing from a list come after the listed ones, by id`() {
        val zebra = SourceId.of("zebra")
        val alpha = SourceId.of("alpha")

        val merged = merge.merge(listOf(candidate(zebra), candidate(alpha)), ISBN)

        assertEquals("Eragon (alpha)", merged.title)
    }

    /** I12, A9: data that may not be stored never rides into a record that may. */
    @Test
    fun `is not storable when any contribution may not be stored`() {
        val finder = candidate(FINDER, coverUrl = null, pageCount = 500, persistable = false)
        val community = candidate(COMMUNITY)

        val merged = merge.merge(listOf(finder, community), ISBN)

        assertEquals(false, merged.persistableInPublicCatalog)
    }

    @Test
    fun `stays storable when the unstorable candidate contributed nothing`() {
        val authority = candidate(AUTHORITY, language = GERMAN, pageCount = 733, publisher = "cbj", coverUrl = null)
        val finder = candidate(FINDER, persistable = false)
        val community = candidate(COMMUNITY, coverUrl = "https://community/c.jpg")

        // German: authority names it and has every text field; the finder has no cover, the community has
        val merged = merge.merge(listOf(authority, finder, community), ISBN)

        assertEquals(true, merged.persistableInPublicCatalog)
        assertEquals("https://community/c.jpg", merged.coverUrl)
    }

    @Test
    fun `a single candidate merges into itself`() {
        val only = candidate(COMMUNITY, publisher = "Knopf", coverUrl = "https://community/c.jpg")

        assertEquals(only, merge.merge(listOf(only), ISBN))
    }

    @Test
    fun `refuses to merge nothing`() {
        assertFailsWith<IllegalArgumentException> { merge.merge(emptyList(), ISBN) }
    }
}
