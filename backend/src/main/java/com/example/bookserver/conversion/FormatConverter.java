package com.example.bookserver.conversion;

import java.nio.file.Path;
import java.util.Set;

/**
 * Extension point for format conversion. A real converter is added by declaring
 * a new Spring bean implementing this interface and advertising the pairs it can
 * handle via {@link #supportedPairs()} — no existing class needs to change
 * (Open/Closed).
 */
public interface FormatConverter {

    /** Directed pairs {@code (source -> target)} this converter can handle. */
    Set<FormatPair> supportedPairs();

    /**
     * Converts {@code source} into {@code targetFormat} and returns the path to the
     * produced file.
     *
     * @throws Exception if conversion cannot be performed
     */
    Path convert(Path source, String targetFormat) throws Exception;
}
