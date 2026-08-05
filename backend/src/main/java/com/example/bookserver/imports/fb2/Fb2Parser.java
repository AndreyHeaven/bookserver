package com.example.bookserver.imports.fb2;

import com.example.bookserver.imports.ImportedAuthor;
import com.example.bookserver.imports.ImportedSeries;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Fb2Parser {

    private static final int ENCODING_PROBE_SIZE = 4_096;
    private static final Pattern XML_ENCODING = Pattern.compile(
            "<\\?xml\\s+[^>]*encoding\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]", Pattern.CASE_INSENSITIVE);

    private final XMLInputFactory inputFactory = createInputFactory();

    /**
     * FB2 is untrusted input, so DTDs and external entities are disabled on the shared
     * factory to prevent XXE and billion-laughs attacks. Valid FB2 (which has no DTD)
     * parses exactly as before.
     */
    private static XMLInputFactory createInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    public Fb2Metadata parse(InputStream input) throws Exception {
        XMLStreamReader reader = inputFactory.createXMLStreamReader(createReader(input));
        List<ImportedAuthor> authors = new ArrayList<>();
        List<String> genres = new ArrayList<>();
        String title = null;
        String lang = null;
        Integer year = null;
        String annotation = null;
        ImportedSeries series = null;
        String coverHref = null;
        byte[] coverImage = null;
        String coverContentType = null;
        boolean inTitleInfo = false;
        boolean inCoverpage = false;
        String currentAuthorField = null;
        String first = null;
        String middle = null;
        String last = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                if ("title-info".equals(name)) {
                    inTitleInfo = true;
                } else if (inTitleInfo && "coverpage".equals(name)) {
                    inCoverpage = true;
                } else if (inTitleInfo && inCoverpage && "image".equals(name)) {
                    if (coverHref == null) {
                        coverHref = stripHash(coverHref(reader));
                    }
                } else if (coverHref != null && "binary".equals(name)
                        && coverHref.equals(reader.getAttributeValue(null, "id"))) {
                    coverContentType = reader.getAttributeValue(null, "content-type");
                    coverImage = decodeBase64(reader.getElementText());
                    break;
                } else if (inTitleInfo && "author".equals(name)) {
                    first = null;
                    middle = null;
                    last = null;
                } else if (inTitleInfo && "sequence".equals(name)) {
                    series = new ImportedSeries(reader.getAttributeValue(null, "name"),
                            parseInteger(reader.getAttributeValue(null, "number")));
                } else if (inTitleInfo && isAuthorField(name)) {
                    currentAuthorField = name;
                } else if (inTitleInfo && "book-title".equals(name)) {
                    title = readElementText(reader);
                } else if (inTitleInfo && "genre".equals(name)) {
                    genres.add(readElementText(reader).trim());
                } else if (inTitleInfo && "lang".equals(name)) {
                    lang = readElementText(reader).trim();
                } else if (inTitleInfo && "date".equals(name)) {
                    year = parseYear(readElementText(reader));
                } else if (inTitleInfo && "annotation".equals(name)) {
                    annotation = readElementText(reader).trim();
                }
            } else if (event == XMLStreamConstants.CHARACTERS && currentAuthorField != null) {
                String value = reader.getText().trim();
                if (!value.isBlank()) {
                    switch (currentAuthorField) {
                        case "first-name" -> first = value;
                        case "middle-name" -> middle = value;
                        case "last-name" -> last = value;
                        default -> { }
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String name = reader.getLocalName();
                if (isAuthorField(name)) {
                    currentAuthorField = null;
                } else if (inTitleInfo && "author".equals(name)) {
                    authors.add(new ImportedAuthor(last, first, middle));
                } else if ("coverpage".equals(name)) {
                    inCoverpage = false;
                } else if ("title-info".equals(name)) {
                    inTitleInfo = false;
                    // If there is a cover reference, keep scanning the document for the
                    // matching <binary>; otherwise avoid reading the whole file for nothing.
                    if (coverHref == null) {
                        break;
                    }
                }
            }
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("FB2 book-title is missing");
        }
        return new Fb2Metadata(title.trim(), authors, genres, series, lang, year, annotation,
                coverImage, coverContentType);
    }

    /**
     * Chooses the character set before StAX processes the XML declaration. Some files in
     * Flibusta declare UTF-8 but are actually UTF-16 with a BOM; others use the widely
     * understood but XML-invalid spelling {@code UTF8}. A BOM is authoritative, otherwise
     * a supported declaration is used and UTF-8 remains the XML default.
     */
    private static Reader createReader(InputStream input) throws Exception {
        PushbackInputStream stream = new PushbackInputStream(new BufferedInputStream(input), ENCODING_PROBE_SIZE);
        byte[] probe = stream.readNBytes(ENCODING_PROBE_SIZE);
        int offset = bomLength(probe);
        Charset charset = charsetFromBom(probe);
        if (charset == null) {
            charset = charsetFromDeclaration(probe);
        }
        stream.unread(probe, offset, probe.length - offset);
        return new InputStreamReader(stream, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    private static int bomLength(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return 3;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF
                || bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return 2;
        }
        return 0;
    }

    private static Charset charsetFromBom(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    private static Charset charsetFromDeclaration(byte[] bytes) {
        Matcher matcher = XML_ENCODING.matcher(new String(bytes, StandardCharsets.ISO_8859_1));
        if (!matcher.find()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(matcher.group(1).trim());
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    /** Reads the xlink href attribute of a {@code <coverpage><image/>} element. */
    private static String coverHref(XMLStreamReader reader) {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if ("href".equals(reader.getAttributeLocalName(i))) {
                return reader.getAttributeValue(i);
            }
        }
        return null;
    }

    private static String stripHash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
    }

    private static byte[] decodeBase64(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Base64.getMimeDecoder().decode(text.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String readElementText(XMLStreamReader reader) throws Exception {
        StringBuilder out = new StringBuilder();
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                out.append(reader.getText()).append(' ');
            }
        }
        return out.toString();
    }

    private static boolean isAuthorField(String name) {
        return "first-name".equals(name) || "middle-name".equals(name) || "last-name".equals(name);
    }

    private static Integer parseYear(String value) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{4})").matcher(value);
        return matcher.find() ? parseInteger(matcher.group(1)) : null;
    }

    private static Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
