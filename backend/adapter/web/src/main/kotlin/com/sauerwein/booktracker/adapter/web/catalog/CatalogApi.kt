package com.sauerwein.booktracker.adapter.web.catalog

import com.sauerwein.booktracker.adapter.web.catalog.dtos.BookCandidateResponse
import com.sauerwein.booktracker.adapter.web.common.config.PROBLEM_DETAIL_SCHEMA_REF
import com.sauerwein.booktracker.adapter.web.common.config.PROBLEM_JSON
import com.sauerwein.booktracker.adapter.web.common.config.TAG_CATALOG
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank

/**
 * The Swagger docs of [CatalogController]. Constraints on parameters live here, not on the
 * controller: Bean Validation forbids an overriding method to add its own.
 */
@Tag(name = TAG_CATALOG)
interface CatalogApi {

    @Operation(
        summary = "Search for books",
        description = """
Searches by title, author or ISBN. Use it when the user types something into the search field.

- **A valid ISBN** (with or without dashes) is looked up like a scan, see `GET /api/v1/catalog/isbn/{isbn}`.
  The result has at most one book. Anything else, including an ISBN with a wrong check digit, is searched as text.
- **Text** is searched in the catalog and in your private books first. Only if that finds few books
  are the public book sources asked as well, and their hits are added to the end of the list.

What to do with a hit depends on its `kind` and `id` (see the `BookCandidateResponse` schema):
`CATALOG` → add it with `POST /api/v1/books/catalog/{id}`; `PRIVATE` → it is already one of your books;
no `id` → it can't be added, but you may use its data to create a private book.

Searching does not change your books.""",
    )
    @ApiResponse(
        responseCode = "200",
        description = "The hits: books from the catalog and your private books first, then hits from the " +
                "public book sources. An empty list means nothing was found.",
    )
    @ApiResponse(
        responseCode = "400",
        description = "`query` is missing or blank.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "The public book sources can't be reached and nothing was found locally. " +
                "Try again later. This does not mean the book is unknown. `code` is `SourceUnavailable`.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    fun search(
        @Parameter(description = "What to search for: words from the title or author, or an ISBN.", example = "Orwell 1984")
        @NotBlank query: String,
    ): List<BookCandidateResponse>

    @Operation(
        summary = "Look up a book by its ISBN",
        description = """
Finds the book with this ISBN. Use it after scanning the barcode on a book.

The catalog is checked first. If the book is not there, the public book sources are asked. A book they
know is stored in the catalog when its data may be stored, so the next lookup is fast.

The answer is one book. Use its `kind` and `id` like a search hit (see `GET /api/v1/catalog/search`).""",
    )
    @ApiResponse(responseCode = "200", description = "The book with this ISBN.")
    @ApiResponse(
        responseCode = "400",
        description = "Not a valid ISBN-10 or ISBN-13. `errors` names the field `isbn`.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "No source knows a book with this ISBN. Let the user enter it as a private book.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "The public book sources can't be reached. Try again later. This does not mean " +
                "the book is unknown, so don't send the user to the manual form yet. `code` is `SourceUnavailable`.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    fun findByIsbn(
        @Parameter(description = "An ISBN-10 or ISBN-13. Dashes and spaces are ignored.", example = "978-0-14-118776-1")
        isbn: String,
    ): BookCandidateResponse
}
