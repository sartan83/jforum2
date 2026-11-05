package net.jforum.rss;

import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Writes RSS 2.0 documents with the same channel/item fields as legacy rss_template.htm. */
public final class RssWriter {

    static final String GENERATOR = "JForum - http://www.jforum.net";

    private RssWriter() {
    }

    public static String write(String title, String link, String description, List<RssItem> items) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter w = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            w.writeStartDocument("UTF-8", "1.0");
            w.writeStartElement("rss");
            w.writeAttribute("version", "2.0");
            w.writeStartElement("channel");
            element(w, "title", title);
            element(w, "link", link);
            element(w, "description", description == null || description.isBlank() ? "No Description Given" : description);
            element(w, "generator", GENERATOR);
            for (RssItem item : items) {
                w.writeStartElement("item");
                element(w, "title", item.title());
                element(w, "description", item.description() == null ? "" : item.description());
                w.writeStartElement("guid");
                w.writeAttribute("isPermaLink", "true");
                w.writeCharacters(item.link());
                w.writeEndElement();
                element(w, "link", item.link());
                if (item.publishDate() != null) {
                    element(w, "pubDate", rfc822(item.publishDate()));
                }
                if (item.author() != null) {
                    element(w, "author", item.author());
                }
                w.writeEndElement();
            }
            w.writeEndElement();
            w.writeEndElement();
            w.writeEndDocument();
            w.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
        return out.toString();
    }

    static String rfc822(Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.atOffset(ZoneOffset.UTC));
    }

    private static void element(XMLStreamWriter w, String name, String value) throws XMLStreamException {
        w.writeStartElement(name);
        w.writeCharacters(value == null ? "" : stripInvalid(value));
        w.writeEndElement();
    }

    /** Removes characters that are not allowed in XML 1.0. */
    static String stripInvalid(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().filter(c -> c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF)
            || (c >= 0xE000 && c <= 0xFFFD) || (c >= 0x10000 && c <= 0x10FFFF)).forEach(sb::appendCodePoint);
        return sb.toString();
    }
}
