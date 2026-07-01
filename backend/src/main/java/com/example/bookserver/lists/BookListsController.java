package com.example.bookserver.lists;

import com.example.bookserver.lists.dto.AddBookToListRequest;
import com.example.bookserver.lists.dto.BookListDto;
import com.example.bookserver.lists.dto.CreateBookListRequest;
import com.example.bookserver.lists.dto.ShareLinkDto;
import com.example.bookserver.lists.dto.UpdateBookListRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lists")
@Tag(name = "Book lists")
@SecurityRequirement(name = "bearerAuth")
public class BookListsController {

    private final BookListsService listsService;
    private final ShareService shareService;

    public BookListsController(BookListsService listsService, ShareService shareService) {
        this.listsService = listsService;
        this.shareService = shareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a book list")
    public BookListDto create(@Valid @RequestBody CreateBookListRequest request) {
        return listsService.create(request);
    }

    @GetMapping
    @Operation(summary = "List my book lists")
    public Page<BookListDto> listMine(Pageable pageable) {
        return listsService.listMine(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a book list with items")
    public BookListDto get(@PathVariable Long id) {
        return listsService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update list title/description")
    public BookListDto update(@PathVariable Long id, @Valid @RequestBody UpdateBookListRequest request) {
        return listsService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a book list")
    public void delete(@PathVariable Long id) {
        listsService.delete(id);
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Add a book to the list")
    public BookListDto addItem(@PathVariable Long id, @Valid @RequestBody AddBookToListRequest request) {
        return listsService.addItem(id, request);
    }

    @DeleteMapping("/{id}/items/{bookId}")
    @Operation(summary = "Remove a book from the list")
    public BookListDto removeItem(@PathVariable Long id, @PathVariable Long bookId) {
        return listsService.removeItem(id, bookId);
    }

    @PutMapping("/{id}/items/order")
    @Operation(summary = "Reorder list items")
    public BookListDto reorder(@PathVariable Long id, @RequestBody List<Long> bookIdsInOrder) {
        return listsService.reorder(id, bookIdsInOrder);
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Create or fetch the public share link + QR")
    public ShareLinkDto share(@PathVariable Long id,
                              @RequestParam(defaultValue = "false") boolean regenerate) {
        return shareService.share(id, regenerate);
    }

    @DeleteMapping("/{id}/share")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the public share link")
    public void revokeShare(@PathVariable Long id) {
        shareService.revoke(id);
    }
}
