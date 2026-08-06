package com.example.bookserver.imports;

import com.example.bookserver.repo.BookRepository;
import com.example.bookserver.storage.ContentDigest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Shared ZIP-import support: source enumeration, import options and checking whether every
 * selected ZIP entry is already represented by a book with the same content digest.
 * Subclasses decide which entries belong to an archive and how they are imported.
 */
public abstract class AbstractZipBookImporter implements BookImporter {

    private final BookRepository bookRepository;

    protected AbstractZipBookImporter(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    protected static boolean stopOnError(ImportContext context) {
        String value = context.options().get("stopOnError");
        return value == null || Boolean.parseBoolean(value);
    }

    protected static ArchiveImportMode archiveImportMode(ImportContext context) {
        String value = context.options().get("archiveImportMode");
        if (value != null) {
            return ArchiveImportMode.from(value);
        }
        return Boolean.parseBoolean(context.options().get("skipExistingArchives"))
                ? ArchiveImportMode.SKIP_BY_HASH
                : ArchiveImportMode.IMPORT_ALL;
    }

    protected final boolean archiveExistsByName(String archiveName) {
        return bookRepository.existsByArchiveName(archiveName);
    }

    /**
     * Returns true only when the selected, non-empty set of readable entries already exists in the catalog.
     */
    protected final boolean allEntriesAlreadyImported(ZipFile zip, Collection<ZipEntry> entries) throws IOException {
        if (entries.isEmpty()) {
            return false;
        }
        List<String> md5s = new ArrayList<>(entries.size());
        for (ZipEntry entry : entries) {
            try (InputStream input = zip.getInputStream(entry)) {
                md5s.add(ContentDigest.of(input).md5());
            }
        }
        Set<String> existingMd5s = bookRepository.findExistingMd5s(md5s);
        return existingMd5s.containsAll(md5s);
    }

    protected static List<ZipEntry> zipEntries(ZipFile zip, Predicate<ZipEntry> filter) {
        List<ZipEntry> result = new ArrayList<>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (filter.test(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    protected static List<Path> listFiles(Path directory, Predicate<String> nameFilter) throws ImportException {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> nameFilter.test(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new ImportException(e.getMessage());
        }
    }

    protected static String entryFileName(ZipEntry entry) {
        return entryFileName(entry.getName());
    }

    protected static String entryFileName(String name) {
        int slash = name.lastIndexOf('/');
        return name.substring(slash + 1).toLowerCase(Locale.ROOT);
    }

    protected static String errorMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    protected enum ArchiveImportMode {
        IMPORT_ALL,
        SKIP_BY_NAME,
        SKIP_BY_HASH;

        private static ArchiveImportMode from(String value) {
            return switch (value) {
                case "importAll" -> IMPORT_ALL;
                case "skipByName" -> SKIP_BY_NAME;
                case "skipByHash" -> SKIP_BY_HASH;
                default -> throw new IllegalArgumentException("Unsupported archive import mode: " + value);
            };
        }
    }
}
