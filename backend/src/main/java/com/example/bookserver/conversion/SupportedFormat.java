package com.example.bookserver.conversion;

import java.util.Locale;

/** Book formats the conversion architecture is aware of. */
public enum SupportedFormat {
    FB2,
    EPUB,
    MOBI,
    PDF,
    AZW3;

    /** Lowercase wire/storage code, e.g. {@code "fb2"}. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
