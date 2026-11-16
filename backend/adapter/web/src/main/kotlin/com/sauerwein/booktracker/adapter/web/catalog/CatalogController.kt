package com.sauerwein.booktracker.adapter.web.catalog

import com.sauerwein.booktracker.adapter.web.catalog.dtos.BookCandidateResponse
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.usecases.FindBookByIsbnUseCase
import com.sauerwein.booktracker.core.book.usecases.SearchCatalogUseCase
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.*


private const val CATALOG_PATH = "catalog"

@RestController
@RequestMapping("/$CATALOG_PATH")
class CatalogController(
    private val searchCatalogUseCase: SearchCatalogUseCase,
    private val findBookByIsbnUseCase: FindBookByIsbnUseCase,
) {
    @GetMapping("/search")
    fun search(@RequestParam @NotBlank query: String): List<BookCandidateResponse> {
        return searchCatalogUseCase(rawQuery = query).map { it.toResponse() }
    }

    @GetMapping("/isbn/{isbn}")
    fun findByIsbn(@PathVariable isbn: String): BookCandidateResponse {
        return findBookByIsbnUseCase(isbn = Isbn.of(rawValue = isbn)).toResponse()
    }
}
