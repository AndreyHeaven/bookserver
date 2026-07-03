package com.example.bookserver.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface BookFileStorage {

    StoredFile store(InputStream input, String fileName) throws IOException;

    /** Stores an existing file as-is (e.g. a whole archive), keyed by its content hash. */
    StoredFile store(Path source) throws IOException;

    InputStream open(String path) throws IOException;

    /** Opens a single entry inside a stored archive. Closing the returned stream closes the archive. */
    InputStream openEntry(String archivePath, String entryName) throws IOException;

    void delete(String path) throws IOException;
}
