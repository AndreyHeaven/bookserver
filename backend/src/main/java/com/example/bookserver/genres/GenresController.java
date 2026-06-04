package com.example.bookserver.genres;

import com.example.bookserver.books.dto.BookCardDto;
import com.example.bookserver.genres.dto.GenreDetailsDto;
import com.example.bookserver.genres.dto.GenreNodeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
@Tag(name = "Genres")
@Validated
public class GenresController {

    private final GenresService service;

    public GenresController(GenresService service) {
        this.service = service;
    }

    @GetMapping("/tree")
    @Operation(summary = "Get genre tree")
    public List<GenreNodeDto> tree() {
        return service.tree();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get genre details")
    public GenreDetailsDto details(@PathVariable Long id) {
        return service.details(id);
    }

    @GetMapping("/{id}/books")
    @Operation(summary = "Get books by genre")
    public Page<BookCardDto> books(@PathVariable Long id,
                                   @RequestParam(defaultValue = "true") boolean includeSubgenres,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false) String lang,
                                   @RequestParam(name = "year_from", required = false) Integer yearFrom,
                                   @RequestParam(name = "year_to", required = false) Integer yearTo,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer size,
                                   @RequestParam(required = false) String sort) {
        return service.books(id, includeSubgenres, q, lang, yearFrom, yearTo, page, size, sort);
    }
}
