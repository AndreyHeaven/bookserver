package com.example.bookserver.telegram;

import com.example.bookserver.books.BookDownloadService.BookFileDownload;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    private final TelegramBotService botService;
    private final TelegramProperties properties;

    public TelegramController(TelegramBotService botService, TelegramProperties properties) {
        this.botService = botService;
        this.properties = properties;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody TelegramUpdate update) {
        if (!properties.enabled() || !properties.webhookSecret().equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        botService.handle(update);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/download/{token}")
    public ResponseEntity<Resource> download(@PathVariable String token) {
        BookFileDownload download = botService.download(token);
        if (download == null) {
            return ResponseEntity.notFound().build();
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(download.contentType());
        if (download.sizeBytes() != null) {
            response.contentLength(download.sizeBytes());
        }
        return response.body(download.resource());
    }
}
