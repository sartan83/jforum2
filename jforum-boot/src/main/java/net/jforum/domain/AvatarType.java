package net.jforum.domain;

/** How the user avatar is stored. */
public enum AvatarType {
    NONE,
    UPLOAD,
    EXTERNAL,
    GALLERY;

    public static AvatarType fromLegacy(int code) {
        AvatarType[] all = values();
        return code >= 0 && code < all.length ? all[code] : all[0];
    }

    public int legacyCode() {
        return ordinal();
    }
}
