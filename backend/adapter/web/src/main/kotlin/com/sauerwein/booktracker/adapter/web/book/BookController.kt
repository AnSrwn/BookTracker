package com.sauerwein.booktracker.adapter.web.book

import com.sauerwein.booktracker.adapter.web.book.dtos.BookResponse
import com.sauerwein.booktracker.adapter.web.book.dtos.CreateBookRequest
import com.sauerwein.booktracker.core.book.models.BookId
import com.sauerwein.booktracker.core.book.usecases.AddBookUseCase
import com.sauerwein.booktracker.core.book.usecases.GetBookUseCase
import com.sauerwein.booktracker.core.book.usecases.GetBooksUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.*


private const val BOOKS_PATH = "books"

@RestController
@RequestMapping("/$BOOKS_PATH")
class BookController(
    private val addBookUseCase: AddBookUseCase,
    private val getBookUseCase: GetBookUseCase,
    private val getBooksUseCase: GetBooksUseCase,
) {
    @PostMapping
    fun create(@RequestBody request: CreateBookRequest): ResponseEntity<BookResponse> {
        val book = addBookUseCase(command = request.toCommand())

        val location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(book.id.value)
            .toUri()

        return ResponseEntity
            .created(location)
            .body(book.toResponse())
    }

    @GetMapping
    fun getAll(): List<BookResponse> {
        return getBooksUseCase().map { it.toResponse() }
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): BookResponse {
        return getBookUseCase(id = BookId(id)).toResponse()
    }
}
