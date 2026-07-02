package com.example.bookserver.opds;

import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serializes an {@link OpdsFeed} into an OPDS 1.2 (Atom) XML document.
 * Stateless and thread-safe: a single {@link XMLOutputFactory} is reused and a
 * fresh {@link XMLStreamWriter} is created per call.
 */
@Component
public class OpdsXmlWriter {

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final OffsetDateTime EPOCH = OffsetDateTime.of(
            1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();

    /** Renders the feed to a UTF-8 encoded Atom XML byte array. */
    public byte[] write(OpdsFeed feed) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            XMLStreamWriter xml = outputFactory.createXMLStreamWriter(out, StandardCharsets.UTF_8.name());
            xml.writeStartDocument(StandardCharsets.UTF_8.name(), "1.0");
            writeFeed(xml, feed);
            xml.writeEndDocument();
            xml.flush();
            xml.close();
        } catch (XMLStreamException e) {
            throw new UncheckedIOException(new java.io.IOException("Failed to render OPDS feed", e));
        }
        return out.toByteArray();
    }

    private void writeFeed(XMLStreamWriter xml, OpdsFeed feed) throws XMLStreamException {
        xml.writeStartElement("feed");
        xml.writeDefaultNamespace(OpdsConstants.ATOM_NS);
        xml.writeNamespace("opds", OpdsConstants.OPDS_NS);
        xml.writeNamespace("dc", OpdsConstants.DC_NS);

        writeTextElement(xml, "id", feed.id());
        writeTextElement(xml, "title", feed.title());
        writeTextElement(xml, "updated", RFC3339.format(orEpoch(feed.updated())));

        writeLink(xml, new OpdsLink(OpdsConstants.REL_SELF, feed.selfHref(), feed.selfType()));
        writeLink(xml, new OpdsLink(OpdsConstants.REL_START, OpdsConstants.BASE_PATH,
                OpdsConstants.NAVIGATION_TYPE));
        if (feed.upHref() != null) {
            writeLink(xml, new OpdsLink(OpdsConstants.REL_UP, feed.upHref(), OpdsConstants.NAVIGATION_TYPE));
        }
        if (feed.prevHref() != null) {
            writeLink(xml, new OpdsLink(OpdsConstants.REL_PREVIOUS, feed.prevHref(), feed.selfType()));
        }
        if (feed.nextHref() != null) {
            writeLink(xml, new OpdsLink(OpdsConstants.REL_NEXT, feed.nextHref(), feed.selfType()));
        }

        for (OpdsEntry entry : feed.entries()) {
            writeEntry(xml, entry);
        }
        xml.writeEndElement();
    }

    private void writeEntry(XMLStreamWriter xml, OpdsEntry entry) throws XMLStreamException {
        xml.writeStartElement("entry");
        writeTextElement(xml, "id", entry.id());
        writeTextElement(xml, "title", entry.title());
        writeTextElement(xml, "updated", RFC3339.format(orEpoch(entry.updated())));

        for (String author : nullSafe(entry.authors())) {
            xml.writeStartElement("author");
            writeTextElement(xml, "name", author);
            xml.writeEndElement();
        }
        for (String category : nullSafe(entry.categories())) {
            xml.writeStartElement("category");
            xml.writeAttribute("term", category);
            xml.writeAttribute("label", category);
            xml.writeEndElement();
        }
        if (entry.content() != null && !entry.content().isBlank()) {
            xml.writeStartElement("content");
            xml.writeAttribute("type", "text");
            xml.writeCharacters(entry.content());
            xml.writeEndElement();
        }
        for (OpdsLink link : nullSafe(entry.links())) {
            writeLink(xml, link);
        }
        xml.writeEndElement();
    }

    private void writeLink(XMLStreamWriter xml, OpdsLink link) throws XMLStreamException {
        xml.writeStartElement("link");
        xml.writeAttribute("rel", link.rel());
        xml.writeAttribute("href", link.href());
        xml.writeAttribute("type", link.type());
        if (link.title() != null) {
            xml.writeAttribute("title", link.title());
        }
        xml.writeEndElement();
    }

    private void writeTextElement(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value == null ? "" : value);
        xml.writeEndElement();
    }

    private static OffsetDateTime orEpoch(OffsetDateTime value) {
        return value == null ? EPOCH : value;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
