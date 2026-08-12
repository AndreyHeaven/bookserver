package com.example.bookserver.books;

import com.example.bookserver.books.BookDownloadService.BookFileDownload;
import com.example.bookserver.books.BookDownloadService.CoverDownload;
import com.example.bookserver.books.dto.BookDetailsDto;
import com.example.bookserver.books.dto.BookSearchRequest;
import com.example.bookserver.books.dto.BookSearchResponse;
import com.example.bookserver.books.dto.FacetCountsDto;
import com.example.bookserver.books.dto.UpdateBookRequest;
import com.example.bookserver.history.BookViewHistoryService;
import com.example.bookserver.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/books")
@Tag(name = "Books")
@Validated
public class BooksController {

    private final BookSearchService service;
    private final BookDownloadService downloadService;
    private final BookAdministrationService administrationService;
    private final BookViewHistoryService historyService;

    public BooksController(BookSearchService service, BookDownloadService downloadService,
                           BookAdministrationService administrationService, BookViewHistoryService historyService) {
        this.service = service;
        this.downloadService = downloadService;
        this.administrationService = administrationService;
        this.historyService = historyService;
    }

    @GetMapping
    @Operation(summary = "Search and browse books")
    public BookSearchResponse search(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) List<String> lang,
                                     @RequestParam(name = "year_from", required = false) Integer yearFrom,
                                     @RequestParam(name = "year_to", required = false) Integer yearTo,
                                     @RequestParam(name = "genre_id", required = false) List<Long> genreId,
                                     @RequestParam(name = "include_subgenres", required = false) Boolean includeSubgenres,
                                     @RequestParam(name = "author_id", required = false) Long authorId,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer size,
                                     @RequestParam(required = false) String sort) {
        return service.search(new BookSearchRequest(q, lang, yearFrom, yearTo, genreId, authorId, page, size, sort), includeSubgenres);
    }

    @GetMapping("/facets")
    @Operation(summary = "Get book facet counts")
    public FacetCountsDto facets(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) List<String> lang,
                                 @RequestParam(name = "year_from", required = false) Integer yearFrom,
                                 @RequestParam(name = "year_to", required = false) Integer yearTo,
                                 @RequestParam(name = "genre_id", required = false) List<Long> genreId,
                                 @RequestParam(name = "include_subgenres", required = false) Boolean includeSubgenres,
                                 @RequestParam(name = "author_id", required = false) Long authorId) {
        return service.facets(new BookSearchRequest(q, lang, yearFrom, yearTo, genreId, authorId, 0, 20, null), includeSubgenres);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get book details")
    public BookDetailsDto details(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal user) {
        BookDetailsDto result = service.getDetails(id);
        if (user != null) historyService.record(user.getId(), id);
        return result;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update editable book metadata")
    public BookDetailsDto update(@PathVariable Long id, @jakarta.validation.Valid @RequestBody UpdateBookRequest request) {
        return administrationService.update(id, request);
    }

    @GetMapping("/{id}/files/{fileId}")
    @Operation(summary = "Download a book file")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId) {
        BookFileDownload download = downloadService.prepare(id, fileId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(download.contentType());
        if (download.sizeBytes() != null) {
            builder.contentLength(download.sizeBytes());
        }
        return builder.body(download.resource());
    }

    @GetMapping("/{id}/cover")
    @Operation(summary = "Get a book cover image")
    public ResponseEntity<Resource> cover(@PathVariable Long id) {
        CoverDownload cover = downloadService.prepareCover(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(cover.contentType())
                .body(cover.resource());
    }
}
