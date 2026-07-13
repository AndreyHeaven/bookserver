package com.example.bookserver.storage;

import java.util.Locale;

/**
 * Controls how the import pipeline persists book files.
 *
 * <ul>
 *   <li>{@link #COPY} — copy every imported file/archive into {@code app.storage.books-dir},
 *       sharded by content hash (the historical default).</li>
 *   <li>{@link #IN_PLACE} — do not copy; reference the original file where it already lives
 *       inside {@code app.imports.base-dir}. Deletion of the import folder breaks downloads.</li>
 * </ul>
 */
public enum StorageMode {

    COPY,
    IN_PLACE;

    /**
     * Parses a configuration value into a mode, accepting both {@code in-place} (config style)
     * and {@code IN_PLACE} (enum style). Blank input falls back to {@link #COPY}.
     */
    public static StorageMode from(String value) {
        if (value == null || value.isBlank()) {
            return COPY;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "COPY" -> COPY;
            case "IN_PLACE", "INPLACE" -> IN_PLACE;
            default -> throw new IllegalArgumentException(
                    "Unknown storage mode '" + value + "'; expected 'copy' or 'in-place'");
        };
    }
}
