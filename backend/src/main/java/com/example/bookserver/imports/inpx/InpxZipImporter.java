package com.example.bookserver.imports.inpx;

import com.example.bookserver.imports.BookImporter;
import com.example.bookserver.imports.ImportContext;
import com.example.bookserver.imports.ImportJobProgress;
import com.example.bookserver.imports.ImportedBook;
import com.example.bookserver.imports.ImportedBookWriter;
import com.example.bookserver.storage.BookFileStorage;
import com.example.bookserver.storage.StoredFile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class InpxZipImporter implements BookImporter {

    private final InpxParser parser;
    private final BookFileStorage storage;
    private final ImportedBookWriter writer;

    public InpxZipImporter(InpxParser parser, BookFileStorage storage, ImportedBookWriter writer) {
        this.parser = parser;
        this.storage = storage;
        this.writer = writer;
    }

    @Override
    public String type() {
        return "inpx-zip";
    }

    @Override
    public String description() {
        return "Imports INPX metadata and zipped FB2 files";
    }

    @Override
    public void importFrom(ImportContext context, ImportJobProgress progress) throws Exception {
        if (!Files.isDirectory(context.sourcePath())) {
            throw new IllegalArgumentException("INPX source must be a directory: " + context.sourcePath());
        }
        List<Path> inpxFiles;
        List<Path> zipFiles;
        try (var stream = Files.list(context.sourcePath())) {
            inpxFiles = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".inpx"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        try (var stream = Files.list(context.sourcePath())) {
            zipFiles = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        if (inpxFiles.isEmpty()) {
            throw new IllegalArgumentException("No .inpx files found in " + context.sourcePath());
        }
        if (zipFiles.isEmpty()) {
            throw new IllegalArgumentException("No book .zip archives found in " + context.sourcePath());
        }
        List<InpxBookRecord> records = inpxFiles.stream()
                .flatMap(path -> {
                    try {
                        return parser.parse(path).stream();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(r -> !r.deleted())
                .toList();
        long total = records.size();
        long processed = 0;
        progress.update(processed, total);
        for (InpxBookRecord record : records) {
            StoredArchiveFile archiveFile = findArchiveFile(record, zipFiles);
            StoredFile stored;
            try (ZipFile zip = new ZipFile(archiveFile.archive().toFile());
                 var input = zip.getInputStream(archiveFile.entry())) {
                stored = storage.store(input, archiveFile.entry().getName());
            }
            writer.write(new ImportedBook(
                    record.title(),
                    record.authors(),
                    record.genres(),
                    record.series(),
                    record.lang(),
                    null,
                    record.keywords(),
                    null,
                    record.extension().toLowerCase(Locale.ROOT),
                    archiveFile.archive().getFileName().toString(),
                    inpxFiles.get(0).getFileName().toString(),
                    stored));
            progress.update(++processed, total);
        }
    }

    private StoredArchiveFile findArchiveFile(InpxBookRecord record, List<Path> zipFiles) throws Exception {
        String expected = record.zipEntryName().toLowerCase(Locale.ROOT);
        for (Path zipPath : zipFiles) {
            if (record.archive() != null
                    && !zipPath.getFileName().toString().equalsIgnoreCase(record.archive())
                    && !zipPath.getFileName().toString().equalsIgnoreCase(record.archive() + ".zip")) {
                continue;
            }
            try (ZipFile zip = new ZipFile(zipPath.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(expected)) {
                        return new StoredArchiveFile(zipPath, entry);
                    }
                }
            }
        }
        throw new IllegalArgumentException("Book file not found in ZIP archives: " + record.zipEntryName());
    }

    private record StoredArchiveFile(Path archive, ZipEntry entry) {
    }
}
