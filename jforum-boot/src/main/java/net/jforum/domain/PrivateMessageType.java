package net.jforum.domain;

/** Legacy privmsgs_type: 0 read, 1 new, 2 sent, 3 saved in, 4 saved out, 5 unread. */
public enum PrivateMessageType {
    READ,
    NEW,
    SENT,
    SAVED_IN,
    SAVED_OUT,
    UNREAD;

    public static PrivateMessageType fromLegacy(int code) {
        PrivateMessageType[] all = values();
        return code >= 0 && code < all.length ? all[code] : all[0];
    }

    public int legacyCode() {
        return ordinal();
    }
}
