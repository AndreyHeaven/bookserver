package com.example.bookserver.books;

import com.example.bookserver.domain.BookFile;
import com.example.bookserver.repo.BookFileRepository;
import com.example.bookserver.storage.BookFileStorage;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;

/**
 * Streams the physical file backing a {@link BookFile} to the client.
 * Validates that the file belongs to the requested (non-deleted) book before opening it.
 */
@Service
@Transactional(readOnly = true)
public class BookDownloadService {

    /** Fallbacks when a book has no title or a file lacks a format. */
    private static final String DEFAULT_FILENAME = "book";
    private static final String DEFAULT_FORMAT = "bin";

    /** Content types for the ebook formats stored by the import pipeline. */
    private static final Map<String, MediaType> CONTENT_TYPES = Map.of(
            "fb2", MediaType.APPLICATION_XML,
            "epub", MediaType.parseMediaType("application/epub+zip"),
            "pdf", MediaType.APPLICATION_PDF,
            "mobi", MediaType.parseMediaType("application/x-mobipocket-ebook"),
            "djvu", MediaType.parseMediaType("image/vnd.djvu"),
            "txt", MediaType.TEXT_PLAIN,
            "rtf", MediaType.parseMediaType("application/rtf"),
            "zip", MediaType.parseMediaType("application/zip"));

    private final BookFileRepository bookFileRepository;
    private final BookFileStorage storage;

    public BookDownloadService(BookFileRepository bookFileRepository, BookFileStorage storage) {
        this.bookFileRepository = bookFileRepository;
        this.storage = storage;
    }

    public BookFileDownload prepare(Long bookId, Long fileId) {
        BookFile file = bookFileRepository.findById(fileId)
                .filter(f -> f.getBook().getId().equals(bookId))
                .filter(f -> !f.getBook().isDeleted())
                .orElseThrow(() -> new EntityNotFoundException(
                        "File " + fileId + " not found for book " + bookId));

        try {
            InputStream stream = file.getEntryName() == null
                    ? storage.open(file.getStoragePath())
                    : storage.openEntry(file.getStoragePath(), file.getEntryName());
            Resource resource = new InputStreamResource(stream);
            return new BookFileDownload(resource, fileName(file), contentType(file), file.getSizeBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open stored file " + file.getStoragePath(), e);
        }
    }

    private static String fileName(BookFile file) {
        String title = file.getBook().getTitle();
        String base = (title == null || title.isBlank()) ? DEFAULT_FILENAME : title.trim();
        String safe = base.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").replaceAll("\\s+", " ").trim();
        String format = file.getFormat() == null ? DEFAULT_FORMAT : file.getFormat().toLowerCase(Locale.ROOT);
        return safe + "." + format;
    }

    private static MediaType contentType(BookFile file) {
        String format = file.getFormat() == null ? "" : file.getFormat().toLowerCase(Locale.ROOT);
        return CONTENT_TYPES.getOrDefault(format, MediaType.APPLICATION_OCTET_STREAM);
    }

    /** Everything needed to build the HTTP download response. */
    public record BookFileDownload(Resource resource, String fileName, MediaType contentType, Long sizeBytes) {
    }
}
