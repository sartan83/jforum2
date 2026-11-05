package net.jforum.config;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Expands ${key} references the way legacy net.jforum.util.preferences.VariableExpander did.
 * Unknown variables are left untouched; cycles are detected and left unexpanded.
 */
public final class VariableExpander {

    private final Function<String, String> source;

    public VariableExpander(Function<String, String> source) {
        this.source = source;
    }

    public String expand(String value) {
        return expand(value, new HashSet<>());
    }

    private String expand(String value, Set<String> visiting) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (pos < value.length()) {
            int start = value.indexOf("${", pos);
            int end = start < 0 ? -1 : value.indexOf('}', start + 2);
            if (start < 0 || end < 0) {
                out.append(value, pos, value.length());
                break;
            }
            out.append(value, pos, start);
            String name = value.substring(start + 2, end);
            String replacement = visiting.contains(name) ? null : source.apply(name);
            if (replacement == null) {
                out.append(value, start, end + 1);
            } else {
                visiting.add(name);
                out.append(expand(replacement, visiting));
                visiting.remove(name);
            }
            pos = end + 1;
        }
        return out.toString();
    }
}
