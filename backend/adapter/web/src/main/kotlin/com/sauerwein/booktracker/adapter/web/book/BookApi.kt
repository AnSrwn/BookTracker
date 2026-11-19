package com.sauerwein.booktracker.adapter.web.book

import com.sauerwein.booktracker.adapter.web.book.dtos.BookResponse
import com.sauerwein.booktracker.adapter.web.book.dtos.CreatePrivateBookRequest
import com.sauerwein.booktracker.adapter.web.common.config.PROBLEM_DETAIL_SCHEMA_REF
import com.sauerwein.booktracker.adapter.web.common.config.PROBLEM_JSON
import com.sauerwein.booktracker.adapter.web.common.config.TAG_MY_BOOKS
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import java.util.*

private const val LOCATION_DESCRIPTION = "Where the book can be read from now on: `GET /api/v1/books/{id}`."

/** The Swagger docs of [BookController]. */
@Tag(name = TAG_MY_BOOKS)
interface BookApi {

    @Operation(
        summary = "Create a private book",
        description = """
Creates a book that only you can see and adds it to your books.

Use it when the catalog doesn't know the book, or when you want your own version of a catalog book
(for example with a different title). For your own version, start from the stored catalog book
(`GET /api/v1/books/{id}`), not from a search hit.

Only `title` is required. You may create several private books with the same ISBN.""",
    )
    @ApiResponse(
        responseCode = "201",
        description = "The private book was created.",
        headers = [Header(name = "Location", description = LOCATION_DESCRIPTION, schema = Schema(type = "string", format = "uri"))],
        content = [Content(schema = Schema(implementation = BookResponse::class))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid input, for example a blank title, an invalid ISBN or an unknown language. " +
                "`errors` lists each field that failed.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    fun createPrivate(request: CreatePrivateBookRequest): ResponseEntity<BookResponse>

    @Operation(
        summary = "Add a catalog book to my books",
        description = """
Adds a book from the shared catalog to your books. This only links the book to you: it is not copied,
and you can't change it. To change its data, create a private book instead.

Pass the `id` of a search or ISBN hit with `kind` = `CATALOG` exactly as you got it. A hit that is not in the
catalog yet is stored there now.""",
    )
    @ApiResponse(
        responseCode = "201",
        description = "The book is now in your books. The body is the catalog book.",
        headers = [Header(name = "Location", description = LOCATION_DESCRIPTION, schema = Schema(type = "string", format = "uri"))],
        content = [Content(schema = Schema(implementation = BookResponse::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "No catalog book can be added with this `id`: it is unknown, no longer valid, or the id of a " +
                "private book. Let the user enter the book as a private book, starting from an **empty** form: " +
                "don't copy the hit's data into it.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "The book is already in your books. `code` is `AlreadyInLibrary`.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    @ApiResponse(
        responseCode = "503",
        description = "The hit is not in the catalog yet, and the public book sources needed to store it can't be reached. " +
                "Try again later. `code` is `SourceUnavailable`.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    fun addCatalogBook(
        @Parameter(
            description = "The `id` of a search or ISBN hit, unchanged. It is either a UUID or a longer signed id; " +
                    "treat it as an opaque string.",
            example = "3f2b8c1e-5d4a-4b7e-9c1f-2a6d8e0b4c71",
        )
        id: String,
    ): ResponseEntity<BookResponse>

    @Operation(
        summary = "List my books",
        description = """
Returns all your books: the catalog books you added and your private books.
It does not list the whole catalog; use search for that.""",
    )
    @ApiResponse(responseCode = "200", description = "Your books. An empty list if you have none yet.")
    fun getAll(): List<BookResponse>

    @Operation(
        summary = "Get one book",
        description = """
Returns one book by its UUID: any catalog book, or one of your private books.
A catalog book does not have to be in your books to be read here.

Use it as the starting point when the user wants their own version of a catalog book.""",
    )
    @ApiResponse(responseCode = "200", description = "The book.")
    @ApiResponse(
        responseCode = "400",
        description = "`id` is not a UUID.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "No book with this id that you may see. Other users' private books count as not found.",
        content = [Content(mediaType = PROBLEM_JSON, schema = Schema(ref = PROBLEM_DETAIL_SCHEMA_REF))],
    )
    fun getById(
        @Parameter(description = "The book's UUID, as `id` in a book or a hit with `kind` = `CATALOG` or `PRIVATE`.")
        id: UUID,
    ): BookResponse
}
