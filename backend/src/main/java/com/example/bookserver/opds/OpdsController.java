package com.example.bookserver.opds;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPDS 1.2 catalog. Serves Atom XML feeds consumable by OPDS-aware ebook
 * readers (FBReader, KOReader, Calibre, etc.). Every feed is rendered by
 * {@link OpdsXmlWriter}; the concrete OPDS content type is taken from the feed.
 */
@RestController
@RequestMapping(OpdsConstants.BASE_PATH)
@Tag(name = "OPDS catalog")
public class OpdsController {

    private final OpdsFeedService feedService;
    private final OpdsXmlWriter xmlWriter;

    public OpdsController(OpdsFeedService feedService, OpdsXmlWriter xmlWriter) {
        this.feedService = feedService;
        this.xmlWriter = xmlWriter;
    }

    @GetMapping
    @Operation(summary = "OPDS root navigation feed")
    public ResponseEntity<byte[]> root() {
        return render(feedService.root());
    }

    @GetMapping("/new")
    @Operation(summary = "OPDS acquisition feed of recently added books")
    public ResponseEntity<byte[]> newBooks(@RequestParam(defaultValue = "0") int page) {
        return render(feedService.newBooks(page));
    }

    @GetMapping("/authors")
    @Operation(summary = "OPDS navigation feed of author alphabet buckets")
    public ResponseEntity<byte[]> authors() {
        return render(feedService.authorsRoot());
    }

    @GetMapping("/authors/letter/{letter}")
    @Operation(summary = "OPDS navigation feed of authors by first letter")
    public ResponseEntity<byte[]> authorsByLetter(@PathVariable String letter,
                                                  @RequestParam(defaultValue = "0") int page) {
        return render(feedService.authorsByLetter(letter, page));
    }

    @GetMapping("/authors/{id}")
    @Operation(summary = "OPDS acquisition feed of books by an author")
    public ResponseEntity<byte[]> authorBooks(@PathVariable Long id,
                                              @RequestParam(defaultValue = "0") int page) {
        return render(feedService.authorBooks(id, page));
    }

    @GetMapping("/genres")
    @Operation(summary = "OPDS navigation feed of top-level genres")
    public ResponseEntity<byte[]> genres() {
        return render(feedService.genresRoot());
    }

    @GetMapping("/genres/{id}")
    @Operation(summary = "OPDS feed of sub-genres and books for a genre")
    public ResponseEntity<byte[]> genre(@PathVariable Long id,
                                        @RequestParam(defaultValue = "0") int page) {
        return render(feedService.genre(id, page));
    }

    @GetMapping("/lists")
    @Operation(summary = "OPDS navigation feed of the current user's book lists")
    public ResponseEntity<byte[]> lists(@RequestParam(defaultValue = "0") int page) {
        return render(feedService.lists(page));
    }

    @GetMapping("/lists/{id}")
    @Operation(summary = "OPDS acquisition feed of a book list's contents")
    public ResponseEntity<byte[]> listBooks(@PathVariable Long id) {
        return render(feedService.listBooks(id));
    }

    private ResponseEntity<byte[]> render(OpdsFeed feed) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(feed.selfType()))
                .body(xmlWriter.write(feed));
    }
}
