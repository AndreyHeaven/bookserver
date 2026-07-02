package com.example.bookserver.opds;

/**
 * A single Atom {@code <link>} inside an OPDS feed or entry.
 *
 * @param rel   link relation (e.g. {@code self}, {@code subsection},
 *              {@code http://opds-spec.org/acquisition})
 * @param href  target URL (absolute path within this server)
 * @param type  MIME type of the target resource
 * @param title optional human-readable title, may be {@code null}
 */
public record OpdsLink(String rel, String href, String type, String title) {

    public OpdsLink(String rel, String href, String type) {
        this(rel, href, type, null);
    }
}
