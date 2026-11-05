package net.jforum.admin;

import java.util.ArrayList;
import java.util.List;

import net.jforum.support.Ids;

final class AdminSupport {

    private AdminSupport() {
    }

    static List<Integer> ids(String[] raw) {
        List<Integer> result = new ArrayList<>();
        if (raw != null) {
            for (String s : raw) {
                Integer id = Ids.parse(s);
                if (id != null && !result.contains(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    static int parseInt(String raw, int fallback) {
        Integer v = Ids.parse(raw);
        return v == null ? fallback : v;
    }

    static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
