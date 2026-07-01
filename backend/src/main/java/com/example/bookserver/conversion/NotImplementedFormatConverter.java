package com.example.bookserver.conversion;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * The only production {@link FormatConverter} bean. It advertises every
 * non-identity pair among {@link SupportedFormat} so the architecture is
 * exercised end-to-end, but always fails with a clear, static message.
 *
 * <p>Disable it by removing {@code @Component} or excluding it via a Spring
 * profile once a real converter is wired in.
 */
@Component
public class NotImplementedFormatConverter implements FormatConverter {

    public static final String MESSAGE = "Conversion not implemented yet";

    @Override
    public Set<FormatPair> supportedPairs() {
        Set<FormatPair> pairs = new HashSet<>();
        for (SupportedFormat source : SupportedFormat.values()) {
            for (SupportedFormat target : SupportedFormat.values()) {
                if (source != target) {
                    pairs.add(new FormatPair(source.code(), target.code()));
                }
            }
        }
        return pairs;
    }

    @Override
    public Path convert(Path source, String targetFormat) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
