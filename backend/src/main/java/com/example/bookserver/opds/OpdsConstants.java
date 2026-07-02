package com.example.bookserver.opds;

import java.util.Locale;
import java.util.Map;

/**
 * Shared constants for the OPDS 1.2 (Atom) catalog: content types, XML
 * namespaces, link relations and the format→MIME mapping used for acquisition
 * links.
 */
public final class OpdsConstants {

    private OpdsConstants() {
    }

    /** Base path of the whole catalog. */
    public static final String BASE_PATH = "/opds";

    /** Content type of an OPDS navigation feed (links to other feeds). */
    public static final String NAVIGATION_TYPE =
            "application/atom+xml;profile=opds-catalog;kind=navigation";

    /** Content type of an OPDS acquisition feed (links to downloadable books). */
    public static final String ACQUISITION_TYPE =
            "application/atom+xml;profile=opds-catalog;kind=acquisition";

    // Atom / OPDS link relations.
    public static final String REL_SELF = "self";
    public static final String REL_START = "start";
    public static final String REL_UP = "up";
    public static final String REL_NEXT = "next";
    public static final String REL_PREVIOUS = "previous";
    public static final String REL_SUBSECTION = "subsection";
    public static final String REL_ACQUISITION = "http://opds-spec.org/acquisition";
    public static final String REL_SORT_NEW = "http://opds-spec.org/sort/new";

    // XML namespaces.
    public static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    public static final String OPDS_NS = "http://opds-spec.org/2010/catalog";
    public static final String DC_NS = "http://purl.org/dc/terms/";

    /** URN prefix used to build stable, unique entry/feed identifiers. */
    public static final String URN_PREFIX = "urn:bookserver:";

    /** Fallback acquisition MIME type for unknown formats. */
    public static final String DEFAULT_ACQUISITION_TYPE = "application/octet-stream";

    /** Ebook format → acquisition MIME type. Mirrors the download endpoint. */
    private static final Map<String, String> ACQUISITION_TYPES = Map.of(
            "fb2", "application/fb2+xml",
            "epub", "application/epub+zip",
            "pdf", "application/pdf",
            "mobi", "application/x-mobipocket-ebook",
            "djvu", "image/vnd.djvu",
            "txt", "text/plain",
            "rtf", "application/rtf",
            "zip", "application/zip");

    /** Resolves the acquisition MIME type for a stored file format. */
    public static String acquisitionType(String format) {
        if (format == null || format.isBlank()) {
            return DEFAULT_ACQUISITION_TYPE;
        }
        return ACQUISITION_TYPES.getOrDefault(format.toLowerCase(Locale.ROOT), DEFAULT_ACQUISITION_TYPE);
    }
}
