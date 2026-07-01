package com.example.bookserver.imports.fb2;

import com.example.bookserver.imports.ImportedAuthor;
import com.example.bookserver.imports.ImportedSeries;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class Fb2Parser {

    private final XMLInputFactory inputFactory = XMLInputFactory.newFactory();

    public Fb2Metadata parse(InputStream input) throws Exception {
        XMLStreamReader reader = inputFactory.createXMLStreamReader(input);
        List<ImportedAuthor> authors = new ArrayList<>();
        List<String> genres = new ArrayList<>();
        String title = null;
        String lang = null;
        Integer year = null;
        String annotation = null;
        ImportedSeries series = null;
        boolean inTitleInfo = false;
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
                } else if ("title-info".equals(name)) {
                    break;
                }
            }
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("FB2 book-title is missing");
        }
        return new Fb2Metadata(title.trim(), authors, genres, series, lang, year, annotation);
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
