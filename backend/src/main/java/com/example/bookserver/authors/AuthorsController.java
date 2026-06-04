package com.example.bookserver.authors;

import com.example.bookserver.authors.dto.AlphabetBucketDto;
import com.example.bookserver.authors.dto.AuthorCardDto;
import com.example.bookserver.authors.dto.AuthorDetailsDto;
import com.example.bookserver.authors.dto.AuthorSearchRequest;
import com.example.bookserver.books.dto.BookCardDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/authors")
@Tag(name = "Authors")
@Validated
public class AuthorsController {

    private final AuthorsService service;

    public AuthorsController(AuthorsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search and browse authors")
    public Page<AuthorCardDto> search(@Valid @ModelAttribute AuthorSearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/alphabet")
    @Operation(summary = "Get author alphabet counts")
    public List<AlphabetBucketDto> alphabet() {
        return service.alphabet();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get author details")
    public AuthorDetailsDto details(@PathVariable Long id) {
        return service.details(id);
    }

    @GetMapping("/{id}/books")
    @Operation(summary = "Get books by author")
    public Page<BookCardDto> books(@PathVariable Long id,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false) String lang,
                                   @RequestParam(name = "year_from", required = false) Integer yearFrom,
                                   @RequestParam(name = "year_to", required = false) Integer yearTo,
                                   @RequestParam(name = "genre_id", required = false) List<Long> genreIds,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer size,
                                   @RequestParam(required = false) String sort) {
        return service.books(id, q, lang, yearFrom, yearTo, genreIds, page, size, sort);
    }
}
