package com.sauerwein.booktracker.adapter.web.book

import com.sauerwein.booktracker.adapter.web.book.dtos.BookResponse
import com.sauerwein.booktracker.adapter.web.book.dtos.CreatePrivateBookRequest
import com.sauerwein.booktracker.adapter.web.common.config.API_PREFIX
import com.sauerwein.booktracker.core.book.CatalogHitIds
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.models.CatalogBookRef
import com.sauerwein.booktracker.core.book.usecases.AddCatalogBookUseCase
import com.sauerwein.booktracker.core.book.usecases.CreatePrivateBookUseCase
import com.sauerwein.booktracker.core.book.usecases.GetBookUseCase
import com.sauerwein.booktracker.core.book.usecases.GetBooksUseCase
import com.sauerwein.booktracker.core.shared.exception.NotFoundException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI
import java.util.*


private const val BOOKS_PATH = "books"

/**
 * The caller's books. The path names the kind of book a write is about: `private` creates
 * the caller's own book, `catalog` adds a shared catalog book without copying it.
 *
 * Swagger docs are in [BookApi].
 */
@RestController
@RequestMapping("/$BOOKS_PATH")
class BookController(
    private val createPrivateBookUseCase: CreatePrivateBookUseCase,
    private val addCatalogBookUseCase: AddCatalogBookUseCase,
    private val getBookUseCase: GetBookUseCase,
    private val getBooksUseCase: GetBooksUseCase,
    private val catalogHitIds: CatalogHitIds,
) : BookApi {
    @PostMapping("/private")
    override fun createPrivate(@Valid @RequestBody request: CreatePrivateBookRequest): ResponseEntity<BookResponse> {
        return created(createPrivateBookUseCase(command = request.toCommand()))
    }

    /** [id] is opaque: a stored book's UUID or a search hit's signed id, exactly as search or scan returned it. */
    @PostMapping("/catalog/{id}")
    override fun addCatalogBook(@PathVariable id: String): ResponseEntity<BookResponse> {
        val ref = runCatching { CatalogBookRef.Stored(BookId(UUID.fromString(id))) }.getOrNull()
            ?: catalogHitIds.decode(id)
            // Forged, tampered or garbled: indistinguishable from an unknown id, on purpose
            ?: throw NotFoundException("Catalog book", id)

        return created(addCatalogBookUseCase(ref))
    }

    @GetMapping
    override fun getAll(): List<BookResponse> {
        return getBooksUseCase().map { it.toResponse() }
    }

    @GetMapping("/{id}")
    override fun getById(@PathVariable id: UUID): BookResponse {
        return getBookUseCase(id = BookId(id)).toResponse()
    }

    private fun created(book: Book): ResponseEntity<BookResponse> {
        return ResponseEntity
            .created(locationOf(book.id))
            .body(book.toResponse())
    }

    /** Every created book lives at `GET /books/{id}`, whichever POST created it. */
    private fun locationOf(id: BookId): URI {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("$API_PREFIX/$BOOKS_PATH/{id}")
            .buildAndExpand(id.value)
            .toUri()
    }
}
