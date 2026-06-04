package com.example.bookserver.books;

import com.example.bookserver.books.dto.BookDetailsDto;
import com.example.bookserver.books.dto.BookSearchRequest;
import com.example.bookserver.books.dto.BookSearchResponse;
import com.example.bookserver.books.dto.FacetCountsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books")
@Tag(name = "Books")
@Validated
public class BooksController {

    private final BookSearchService service;

    public BooksController(BookSearchService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search and browse books")
    public BookSearchResponse search(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) String lang,
                                     @RequestParam(name = "year_from", required = false) Integer yearFrom,
                                     @RequestParam(name = "year_to", required = false) Integer yearTo,
                                     @RequestParam(name = "genre_id", required = false) List<Long> genreId,
                                     @RequestParam(name = "author_id", required = false) Long authorId,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer size,
                                     @RequestParam(required = false) String sort) {
        return service.search(new BookSearchRequest(q, lang, yearFrom, yearTo, genreId, authorId, page, size, sort));
    }

    @GetMapping("/facets")
    @Operation(summary = "Get book facet counts")
    public FacetCountsDto facets(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) String lang,
                                 @RequestParam(name = "year_from", required = false) Integer yearFrom,
                                 @RequestParam(name = "year_to", required = false) Integer yearTo,
                                 @RequestParam(name = "genre_id", required = false) List<Long> genreId,
                                 @RequestParam(name = "author_id", required = false) Long authorId) {
        return service.facets(new BookSearchRequest(q, lang, yearFrom, yearTo, genreId, authorId, 0, 20, null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get book details")
    public BookDetailsDto details(@PathVariable Long id) {
        return service.getDetails(id);
    }
}
