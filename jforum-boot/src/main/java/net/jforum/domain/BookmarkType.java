package net.jforum.domain;

/** Legacy relation_type: 1 forum, 2 topic, 3 user. */
public enum BookmarkType {
    UNUSED,
    FORUM,
    TOPIC,
    USER;

    public static BookmarkType fromLegacy(int code) {
        BookmarkType[] all = values();
        return code >= 0 && code < all.length ? all[code] : all[0];
    }

    public int legacyCode() {
        return ordinal();
    }
}
