package net.jforum.domain;

/** Legacy topic_type: 0 normal, 1 sticky, 2 announce. */
public enum TopicType {
    NORMAL,
    STICKY,
    ANNOUNCE;

    public static TopicType fromLegacy(int code) {
        TopicType[] all = values();
        return code >= 0 && code < all.length ? all[code] : all[0];
    }

    public int legacyCode() {
        return ordinal();
    }
}
