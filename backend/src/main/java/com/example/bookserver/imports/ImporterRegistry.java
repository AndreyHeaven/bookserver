package com.example.bookserver.imports;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ImporterRegistry {

    private final Map<String, BookImporter> importers;

    public ImporterRegistry(List<BookImporter> importers) {
        this.importers = importers.stream()
                .collect(Collectors.toUnmodifiableMap(BookImporter::type, Function.identity()));
    }

    public BookImporter get(String type) {
        BookImporter importer = importers.get(type);
        if (importer == null) {
            throw new IllegalArgumentException("Unknown importer type: " + type);
        }
        return importer;
    }
}
