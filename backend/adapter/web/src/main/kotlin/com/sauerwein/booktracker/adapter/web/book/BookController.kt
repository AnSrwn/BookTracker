package com.sauerwein.booktracker.adapter.web.book

import com.sauerwein.booktracker.adapter.web.book.dtos.BookResponse
import com.sauerwein.booktracker.adapter.web.book.dtos.CreatePrivateBookRequest
import com.sauerwein.booktracker.core.book.models.Book
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.usecases.AddCatalogBookUseCase
import com.sauerwein.booktracker.core.book.usecases.CreatePrivateBookUseCase
import com.sauerwein.booktracker.core.book.usecases.GetBookUseCase
import com.sauerwein.booktracker.core.book.usecases.GetBooksUseCase
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
 */
@RestController
@RequestMapping("/$BOOKS_PATH")
class BookController(
    private val createPrivateBookUseCase: CreatePrivateBookUseCase,
    private val addCatalogBookUseCase: AddCatalogBookUseCase,
    private val getBookUseCase: GetBookUseCase,
    private val getBooksUseCase: GetBooksUseCase,
) {
    @PostMapping("/private")
    fun createPrivate(@Valid @RequestBody request: CreatePrivateBookRequest): ResponseEntity<BookResponse> {
        return created(createPrivateBookUseCase(command = request.toCommand()))
    }

    @PostMapping("/catalog/{id}")
    fun addCatalogBook(@PathVariable id: UUID): ResponseEntity<BookResponse> {
        return created(addCatalogBookUseCase(id = BookId(id)))
    }

    @GetMapping
    fun getAll(): List<BookResponse> {
        return getBooksUseCase().map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): BookResponse {
        return getBookUseCase(id = BookId(id)).toResponse()
    }

    private fun created(book: Book): ResponseEntity<BookResponse> {
        return ResponseEntity
            .created(locationOf(book.id))
            .body(book.toResponse())
    }

    /** `/…/books/private` and `/…/books/catalog/{id}` both point at `/…/books/{id}`; keeps the `/api/v1` prefix. */
    private fun locationOf(id: BookId): URI {
        val requestPath = ServletUriComponentsBuilder.fromCurrentRequestUri().build().path.orEmpty()
        val booksPath = requestPath.substringBeforeLast("/$BOOKS_PATH/") + "/$BOOKS_PATH/{id}"

        return ServletUriComponentsBuilder.fromCurrentRequestUri()
            .replacePath(booksPath)
            .buildAndExpand(id.value)
            .toUri()
    }
}
