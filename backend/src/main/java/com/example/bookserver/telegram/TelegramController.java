package com.example.bookserver.telegram;

import com.example.bookserver.books.BookDownloadService.BookFileDownload;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    private final TelegramBotService botService;

    public TelegramController(TelegramBotService botService) {
        this.botService = botService;
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
