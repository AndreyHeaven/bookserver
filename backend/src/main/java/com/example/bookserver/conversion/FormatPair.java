package com.example.bookserver.conversion;

import java.util.Locale;

/**
 * Directed source/target format pair used as the registry key.
 * Both components are normalized to lowercase so lookups are case-insensitive.
 */
public record FormatPair(String sourceFormat, String targetFormat) {

    public FormatPair {
        sourceFormat = normalize(sourceFormat);
        targetFormat = normalize(targetFormat);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
