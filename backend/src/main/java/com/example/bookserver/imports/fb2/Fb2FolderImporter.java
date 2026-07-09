package com.example.bookserver.imports.fb2;

import com.example.bookserver.imports.BookImporter;
import com.example.bookserver.imports.ImportContext;
import com.example.bookserver.imports.ImportJobProgress;
import com.example.bookserver.imports.ImportedBook;
import com.example.bookserver.imports.ImportedBookWriter;
import com.example.bookserver.storage.BookFileStorage;
import com.example.bookserver.storage.ContentDigest;
import com.example.bookserver.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class Fb2FolderImporter implements BookImporter {

    private static final Logger log = LoggerFactory.getLogger(Fb2FolderImporter.class);

    private static final String FB2_SUFFIX = ".fb2";
    private static final String ZIP_SUFFIX = ".zip";
    private static final String FB2_FORMAT = "fb2";

    private final Fb2Parser parser;
    private final BookFileStorage storage;
    private final ImportedBookWriter writer;

    public Fb2FolderImporter(Fb2Parser parser, BookFileStorage storage, ImportedBookWriter writer) {
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
        return "Imports standalone FB2 files and FB2 files packed inside ZIP archives from a folder";
    }

    @Override
    public void importFrom(ImportContext context, ImportJobProgress progress) throws Exception {
        if (!Files.isDirectory(context.sourcePath())) {
            throw new IllegalArgumentException("FB2 source must be a directory: " + context.sourcePath());
        }
        List<Path> fb2Files = listFiles(context.sourcePath(), name -> name.endsWith(FB2_SUFFIX));
        List<Path> archives = listFiles(context.sourcePath(), name -> name.endsWith(ZIP_SUFFIX));

        long total = fb2Files.size() + countArchivedFb2(archives);
        long processed = 0;
        progress.update(processed, total);

        for (Path file : fb2Files) {
            importStandalone(file);
            progress.update(++processed, total);
        }
        for (Path archive : archives) {
            processed = importArchive(archive, progress, processed, total);
        }
    }

    /** Stores a standalone FB2 file directly (no archive entry reference). */
    private void importStandalone(Path file) throws Exception {
        Fb2Metadata metadata;
        try (InputStream input = Files.newInputStream(file)) {
            metadata = parser.parse(input);
        }
        StoredFile stored;
        try (InputStream input = Files.newInputStream(file)) {
            stored = storage.store(input, file.getFileName().toString());
        }
        write(metadata, stored, null, null);
    }

    /** Copies an archive into storage once and imports every FB2 entry it contains. */
    private long importArchive(Path archivePath, ImportJobProgress progress, long processed, long total)
            throws Exception {
        String archiveName = archivePath.getFileName().toString();
        StoredFile storedArchive = storage.store(archivePath);
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!isFb2Entry(entry)) {
                    continue;
                }
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
            }
        }
        return processed;
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

    private static List<Path> listFiles(Path directory, Predicate<String> nameFilter) throws Exception {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> nameFilter.test(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }
}
