package com.example.bookserver.opds;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * An Atom {@code <entry>} in an OPDS feed. Depending on its links it acts as a
 * navigation entry (points to another feed) or an acquisition entry (points to
 * downloadable book files).
 *
 * @param id         stable, unique identifier (URN)
 * @param title      entry title
 * @param updated    last-modified timestamp
 * @param content    optional plain-text summary/annotation, may be {@code null}
 * @param authors    author display names (may be empty)
 * @param categories category labels, e.g. genre titles (may be empty)
 * @param links      navigation and/or acquisition links (at least one)
 */
public record OpdsEntry(String id,
                        String title,
                        OffsetDateTime updated,
                        String content,
                        List<String> authors,
                        List<String> categories,
                        List<OpdsLink> links) {
}
