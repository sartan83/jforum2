package net.jforum.support;

/** Request parameter helpers: legacy forms often post blank hidden ids. */
public final class Ids {

    private Ids() {
    }

    public static Integer parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
