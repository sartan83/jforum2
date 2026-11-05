package net.jforum.rss;

import java.time.Instant;

/** Legacy RSSItem. */
public record RssItem(String title, String link, String description, Instant publishDate, String author) {
}
