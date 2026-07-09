package com.example.bookserver.books;

import com.example.bookserver.domain.Book;
import com.example.bookserver.domain.BookAuthor;
import com.example.bookserver.domain.BookFile;
import com.example.bookserver.domain.Person;
import com.example.bookserver.repo.BookFileRepository;
import com.example.bookserver.repo.BookRepository;
import com.example.bookserver.storage.BookFileStorage;
import com.example.bookserver.storage.CoverStorage;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final BookRepository bookRepository;
    private final BookFileStorage storage;
    private final CoverStorage coverStorage;

    public BookDownloadService(BookFileRepository bookFileRepository,
                               BookRepository bookRepository,
                               BookFileStorage storage,
                               CoverStorage coverStorage) {
        this.bookFileRepository = bookFileRepository;
        this.bookRepository = bookRepository;
        this.storage = storage;
        this.coverStorage = coverStorage;
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

    public CoverDownload prepareCover(long bookId) {
        Book book = bookRepository.findById(bookId)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("Book " + bookId + " not found"));
        if (book.getCoverPath() == null) {
            throw new EntityNotFoundException("Book " + bookId + " has no cover");
        }
        try {
            InputStream stream = coverStorage.open(book.getCoverPath());
            Resource resource = new InputStreamResource(stream);
            return new CoverDownload(resource, coverContentType(book.getCoverContentType()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open cover for book " + bookId, e);
        }
    }

    /**
     * Parses the stored cover content type, falling back to {@code application/octet-stream}
     * when it is missing or malformed (FB2 is untrusted input) so bad data never causes a 500.
     */
    private static MediaType coverContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String fileName(BookFile file) {
        Book book = file.getBook();
        String title = book.getTitle();
        String base = (title == null || title.isBlank()) ? DEFAULT_FILENAME : title.trim();
        String authors = authorsPrefix(book);
        if (!authors.isBlank()) {
            base = authors + " - " + base;
        }
        String safe = base.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").replaceAll("\\s+", " ").trim();
        String format = file.getFormat() == null ? DEFAULT_FORMAT : file.getFormat().toLowerCase(Locale.ROOT);
        return safe + "." + format;
    }

    /**
     * Builds the author part of the file name: the first author (by position)
     * followed by "и другие" when the book has more than one.
     */
    private static String authorsPrefix(Book book) {
        List<BookAuthor> authors = book.getAuthors().stream()
                .sorted(Comparator.comparingInt(BookAuthor::getPosition))
                .toList();
        if (authors.isEmpty()) {
            return "";
        }
        String first = personName(authors.getFirst().getPerson());
        if (first.isBlank()) {
            return "";
        }
        return authors.size() > 1 ? first + " и другие" : first;
    }

    /** Joins the available name parts of a person into a single display name. */
    private static String personName(Person person) {
        return Stream.of(person.getLastName(), person.getFirstName(), person.getMiddleName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
    }

    private static MediaType contentType(BookFile file) {
        String format = file.getFormat() == null ? "" : file.getFormat().toLowerCase(Locale.ROOT);
        return CONTENT_TYPES.getOrDefault(format, MediaType.APPLICATION_OCTET_STREAM);
    }

    /** Everything needed to build the HTTP download response. */
    public record BookFileDownload(Resource resource, String fileName, MediaType contentType, Long sizeBytes) {
    }

    /** Everything needed to build the HTTP cover response. */
    public record CoverDownload(Resource resource, MediaType contentType) {
    }
}
