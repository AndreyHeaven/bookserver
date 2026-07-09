package com.example.bookserver.imports.fb2;

import com.example.bookserver.imports.ImportedAuthor;
import com.example.bookserver.imports.ImportedSeries;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class Fb2Parser {

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
        XMLStreamReader reader = inputFactory.createXMLStreamReader(input);
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
                    title = reader.getElementText();
                } else if (inTitleInfo && "genre".equals(name)) {
                    genres.add(reader.getElementText().trim());
                } else if (inTitleInfo && "lang".equals(name)) {
                    lang = reader.getElementText().trim();
                } else if (inTitleInfo && "date".equals(name)) {
                    year = parseYear(reader.getElementText());
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
