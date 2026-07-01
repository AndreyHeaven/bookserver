package com.example.bookserver.conversion.exception;

/** Raised when no {@code FormatConverter} is registered for a requested pair. */
public class ConverterNotFoundException extends RuntimeException {

    public ConverterNotFoundException(String sourceFormat, String targetFormat) {
        super("No converter registered for %s -> %s".formatted(sourceFormat, targetFormat));
    }
}
