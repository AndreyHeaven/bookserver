package com.example.bookserver.imports.fb2;

import com.example.bookserver.imports.BookImporter;
import com.example.bookserver.imports.ImportContext;
import com.example.bookserver.imports.ImportJobProgress;
import com.example.bookserver.imports.ImportedBook;
import com.example.bookserver.imports.ImportedBookWriter;
import com.example.bookserver.storage.BookFileStorage;
import com.example.bookserver.storage.StoredFile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Component
public class Fb2FolderImporter implements BookImporter {

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
        return "Imports standalone FB2 files from a folder";
    }

    @Override
    public void importFrom(ImportContext context, ImportJobProgress progress) throws Exception {
        if (!Files.isDirectory(context.sourcePath())) {
            throw new IllegalArgumentException("FB2 source must be a directory: " + context.sourcePath());
        }
        List<Path> files;
        try (var stream = Files.list(context.sourcePath())) {
            files = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".fb2"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        long total = files.size();
        long processed = 0;
        progress.update(processed, total);
        for (Path file : files) {
            Fb2Metadata metadata;
            try (InputStream input = Files.newInputStream(file)) {
                metadata = parser.parse(input);
            }
            StoredFile stored;
            try (InputStream input = Files.newInputStream(file)) {
                stored = storage.store(input, file.getFileName().toString());
            }
            writer.write(new ImportedBook(
                    metadata.title(),
                    metadata.authors(),
                    metadata.genres(),
                    metadata.series(),
                    metadata.lang(),
                    metadata.year(),
                    null,
                    metadata.annotation(),
                    "fb2",
                    null,
                    null,
                    stored));
            progress.update(++processed, total);
        }
    }
}
