package net.jforum.domain;

/** Legacy log_type. */
public enum ModerationLogType {
    UNKNOWN,
    EDIT,
    DELETE,
    LOCK,
    UNLOCK,
    MOVE,
    APPROVE,
    REJECT;

    public static ModerationLogType fromLegacy(int code) {
        ModerationLogType[] all = values();
        return code >= 0 && code < all.length ? all[code] : all[0];
    }

    public int legacyCode() {
        return ordinal();
    }
}
