package com.sauerwein.booktracker.adapter.web.catalog

import com.sauerwein.booktracker.adapter.web.catalog.dtos.BookCandidateResponse
import com.sauerwein.booktracker.core.book.CatalogHitIds
import com.sauerwein.booktracker.core.book.models.Isbn
import com.sauerwein.booktracker.core.book.usecases.FindBookByIsbnUseCase
import com.sauerwein.booktracker.core.book.usecases.SearchCatalogUseCase
import org.springframework.web.bind.annotation.*


private const val CATALOG_PATH = "catalog"

/** Swagger docs and the parameter constraints are in [CatalogApi]. */
@RestController
@RequestMapping("/$CATALOG_PATH")
class CatalogController(
    private val searchCatalogUseCase: SearchCatalogUseCase,
    private val findBookByIsbnUseCase: FindBookByIsbnUseCase,
    private val catalogHitIds: CatalogHitIds,
) : CatalogApi {
    @GetMapping("/search")
    override fun search(@RequestParam query: String): List<BookCandidateResponse> {
        return searchCatalogUseCase(rawQuery = query).map { it.toResponse(catalogHitIds) }
    }

    @GetMapping("/isbn/{isbn}")
    override fun findByIsbn(@PathVariable isbn: String): BookCandidateResponse {
        return findBookByIsbnUseCase(isbn = Isbn.of(rawValue = isbn)).toResponse(catalogHitIds)
    }
}
