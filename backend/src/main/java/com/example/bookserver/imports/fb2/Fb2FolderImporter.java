package com.example.bookserver.imports.fb2;

import com.example.bookserver.imports.AbstractZipBookImporter;
import com.example.bookserver.imports.ImportContext;
import com.example.bookserver.imports.ImportException;
import com.example.bookserver.imports.ImportJobProgress;
import com.example.bookserver.imports.ImportedBook;
import com.example.bookserver.imports.ImportedBookWriter;
import com.example.bookserver.repo.BookRepository;
import com.example.bookserver.storage.BookFileStorage;
import com.example.bookserver.storage.ContentDigest;
import com.example.bookserver.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class Fb2FolderImporter extends AbstractZipBookImporter {

    private static final Logger log = LoggerFactory.getLogger(Fb2FolderImporter.class);

    private static final String FB2_SUFFIX = ".fb2";
    private static final String ZIP_SUFFIX = ".zip";
    private static final String FB2_FORMAT = "fb2";

    private final Fb2Parser parser;
    private final BookFileStorage storage;
    private final ImportedBookWriter writer;

    public Fb2FolderImporter(Fb2Parser parser,
                             BookFileStorage storage,
                             ImportedBookWriter writer,
                             BookRepository bookRepository) {
        super(bookRepository);
        this.parser = parser;
        this.storage = storage;
        this.writer = writer;
    }

    @Override
    public String type() {
        return "fb2-folder";
    }

    @Override
    public String description() {
        return "Imports standalone FB2 files and FB2 files packed inside a ZIP archive from a folder or archive path";
    }

    @Override
    public void importFrom(ImportContext context, ImportJobProgress progress) throws ImportException {
        Path source = context.sourcePath();
        List<Path> fb2Files;
        List<Path> archives;
        if (Files.isDirectory(source)) {
            fb2Files = listFiles(source, name -> name.endsWith(FB2_SUFFIX));
            archives = listFiles(source, name -> name.endsWith(ZIP_SUFFIX));
        } else if (isZipFile(source)) {
            fb2Files = List.of();
            archives = List.of(source);
        } else {
            throw new IllegalArgumentException("FB2 source must be a directory or ZIP archive: " + source);
        }

        long total = fb2Files.size() + countArchivedFb2(archives);
        long processed = 0;
        boolean stopOnError = stopOnError(context);
        ArchiveImportMode archiveImportMode = archiveImportMode(context);
        progress.update(processed, total);

        for (Path file : fb2Files) {
            try {
                importStandalone(file);
            } catch (Exception e) {
                handleError(progress, stopOnError, "Failed to import file " + file + ": " + errorMessage(e));
            }
            progress.update(++processed, total);
        }
        for (Path archive : archives) {
            try {
                processed = importArchive(archive, progress, processed, total, stopOnError, archiveImportMode);
            } catch (Exception e) {
                handleError(progress, stopOnError, "Failed to import archive " + archive + ": " + errorMessage(e));
            }
        }
    }

    /** Stores a standalone FB2 file directly (no archive entry reference). */
    private void importStandalone(Path file) throws ImportException {
        Fb2Metadata metadata;
        try (InputStream input = Files.newInputStream(file)) {
            metadata = parser.parse(input);
            // store(Path) lets the storage layer decide whether to copy or reference in place.
            StoredFile stored = storage.store(file);
            write(metadata, stored, null, null);
        } catch (Exception e) {
            log.error("Error parsing fb2 file: {}", file, e);
            throw new ImportException("Error parsing fb2 file: "+file);
        }
    }

    /** Copies an archive into storage once and imports every FB2 entry it contains. */
    private long importArchive(Path archivePath, ImportJobProgress progress, long processed, long total,
                               boolean stopOnError, ArchiveImportMode archiveImportMode) throws ImportException {
        try {
            String archiveName = archivePath.getFileName().toString();
            try (ZipFile zip = new ZipFile(archivePath.toFile())) {
                List<ZipEntry> fb2Entries = fb2Entries(zip);
                if (shouldSkipArchive(archiveImportMode, archiveName, zip, fb2Entries)) {
                    log.info("Skipping archive {} using {}", archivePath, archiveImportMode);
                    processed += fb2Entries.size();
                    progress.update(processed, total);
                    return processed;
                }
                StoredFile storedArchive = storage.store(archivePath);
                for (ZipEntry entry : fb2Entries) {
                    try {
                        Fb2Metadata metadata;
                        try (InputStream input = zip.getInputStream(entry)) {
                            metadata = parser.parse(input);
                        }
                        // Book dedup is by the entry's own content hash, not the archive's.
                        ContentDigest digest;
                        try (InputStream input = zip.getInputStream(entry)) {
                            digest = ContentDigest.of(input);
                        }
                        StoredFile stored = new StoredFile(storedArchive.path(), digest.size(), digest.md5());
                        write(metadata, stored, archiveName, entry.getName());
                        progress.update(++processed, total);
                    } catch (Exception e) {
                        String message = "Failed to import archive entry " + archivePath + "!" + entry.getName()
                                + ": " + errorMessage(e);
                        log.error(message, e);
                        if (stopOnError) {
                            throw new ImportException(message);
                        }
                        progress.error(message);
                        progress.update(++processed, total);
                    }
                }
            }
            return processed;
        } catch (Exception e) {
            throw new ImportException(e.getMessage());
        }
    }



    private boolean shouldSkipArchive(ArchiveImportMode mode, String archiveName, ZipFile zip,
                                      List<ZipEntry> entries) throws Exception {
        return switch (mode) {
            case IMPORT_ALL -> false;
            case SKIP_BY_NAME -> archiveExistsByName(archiveName);
            case SKIP_BY_HASH -> allEntriesAlreadyImported(zip, entries);
        };
    }

    private static void handleError(ImportJobProgress progress, boolean stopOnError, String message)
            throws ImportException {
        if (stopOnError) {
            throw new ImportException(message);
        }
        progress.error(message);
    }


    private void write(Fb2Metadata metadata, StoredFile stored, String archiveName, String entryName) {
        writer.write(new ImportedBook(
                metadata.title(),
                metadata.authors(),
                metadata.genres(),
                metadata.series(),
                metadata.lang(),
                metadata.year(),
                null,
                metadata.annotation(),
                FB2_FORMAT,
                archiveName,
                null,
                stored,
                entryName,
                metadata.coverImage(),
                metadata.coverContentType()));
    }

    private long countArchivedFb2(List<Path> archives) {
        long count = 0;
        for (Path archive : archives) {
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    if (isFb2Entry(entries.nextElement())) {
                        count++;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to inspect archive {} while counting FB2 entries: {}",
                        archive.getFileName(), e.getMessage());
            }
        }
        return count;
    }

    private static boolean isFb2Entry(ZipEntry entry) {
        return !entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(FB2_SUFFIX);
    }

    private static boolean isZipFile(Path path) {
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX);
    }

    private static List<ZipEntry> fb2Entries(ZipFile zip) {
        return zipEntries(zip, Fb2FolderImporter::isFb2Entry);
    }
}
