package com.example.bookserver.imports;

public interface BookImporter {

    String type();

    String description();

    void importFrom(ImportContext context, ImportJobProgress progress) throws ImportException;
}
