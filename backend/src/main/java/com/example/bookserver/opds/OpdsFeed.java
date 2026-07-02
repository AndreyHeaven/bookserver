package com.example.bookserver.opds;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Top-level OPDS feed model, rendered to Atom XML by {@link OpdsXmlWriter}.
 *
 * @param id       stable, unique identifier (URN)
 * @param title    feed title
 * @param updated  last-modified timestamp
 * @param selfHref canonical path of this feed
 * @param selfType MIME type of this feed ({@code navigation} or {@code acquisition})
 * @param upHref   optional parent-feed path ({@code rel=up}), may be {@code null}
 * @param prevHref optional previous-page path ({@code rel=previous}), may be {@code null}
 * @param nextHref optional next-page path ({@code rel=next}), may be {@code null}
 * @param entries  feed entries
 */
public record OpdsFeed(String id,
                       String title,
                       OffsetDateTime updated,
                       String selfHref,
                       String selfType,
                       String upHref,
                       String prevHref,
                       String nextHref,
                       List<OpdsEntry> entries) {
}
