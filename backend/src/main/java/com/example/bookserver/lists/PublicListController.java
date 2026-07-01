package com.example.bookserver.lists;

import com.example.bookserver.lists.dto.PublicBookListDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated view of a shared book list. Whitelisted under {@code /api/public/**}. */
@RestController
@RequestMapping("/api/public/lists")
@Tag(name = "Public book lists")
public class PublicListController {

    private final ShareService shareService;

    public PublicListController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "View a shared book list by token")
    public PublicBookListDto view(@PathVariable String token) {
        return shareService.viewPublic(token);
    }

    @GetMapping(value = "/{token}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "QR code PNG for the public list URL")
    public ResponseEntity<byte[]> qr(@PathVariable String token) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(shareService.qrPng(token));
    }
}
