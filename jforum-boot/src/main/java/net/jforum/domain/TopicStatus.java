package net.jforum.domain;

/** Legacy topic_status: 0 unlocked, 1 locked. */
public enum TopicStatus {
    UNLOCKED,
    LOCKED;

    public static TopicStatus fromLegacy(int code) {
        TopicStatus[] all = values();
        return code >= 0 && code < all.length ? all[code] : all[0];
    }

    public int legacyCode() {
        return ordinal();
    }
}
