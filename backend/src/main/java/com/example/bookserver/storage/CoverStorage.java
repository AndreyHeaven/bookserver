package com.example.bookserver.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Stores extracted book cover images on the file system, content-addressed by the
 * image's own MD5 hash. Mirrors {@link BookFileStorage} but is dedicated to covers.
 */
public interface CoverStorage {

    /**
     * Stores the given cover image bytes keyed by their MD5 hash.
     *
     * @param data        raw image bytes
     * @param contentType image content type (e.g. {@code image/jpeg}); drives the file extension
     * @return the stored cover path, relative to the covers base directory
     */
    String store(byte[] data, String contentType) throws IOException;

    /** Opens a stored cover for reading. */
    InputStream open(String path) throws IOException;
}
