package net.jforum.migration;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Typed, null-safe access to the current row of a legacy result set. Column lookup is case-insensitive and
 * ignores surrounding blanks (the SQL Server schema has a {@code [description ]} column). Missing columns read as null,
 * so optional/older columns don't break the import.
 */
final class SourceRow {

    private final ResultSet rs;
    private final ZoneId zone;
    private final Map<String, Integer> index = new HashMap<>();

    SourceRow(ResultSet rs, ZoneId zone) throws SQLException {
        this.rs = rs;
        this.zone = zone;
        ResultSetMetaData md = rs.getMetaData();
        for (int i = md.getColumnCount(); i >= 1; i--) {
            index.put(md.getColumnLabel(i).trim().toLowerCase(Locale.ROOT), i);
        }
    }

    boolean has(String column) {
        return index.containsKey(column.toLowerCase(Locale.ROOT));
    }

    private Integer col(String column) {
        return index.get(column.toLowerCase(Locale.ROOT));
    }

    Object raw(String column) throws SQLException {
        Integer i = col(column);
        return i == null ? null : rs.getObject(i);
    }

    /** Nullable integer; numeric strings are parsed, blank/non-numeric strings are null. */
    Integer integer(String column) throws SQLException {
        Object o = raw(column);
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        String s = o.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Reference id: null, blank and the legacy "no reference" value 0 all become null. */
    Integer id(String column) throws SQLException {
        Integer v = integer(column);
        return v == null || v == 0 ? null : v;
    }

    int intValue(String column, int defaultValue) throws SQLException {
        Integer v = integer(column);
        return v == null ? defaultValue : v;
    }

    long longValue(String column, long defaultValue) throws SQLException {
        Object o = raw(column);
        if (o instanceof Number n) {
            return n.longValue();
        }
        Integer v = integer(column);
        return v == null ? defaultValue : v;
    }

    double doubleValue(String column) throws SQLException {
        Object o = raw(column);
        if (o instanceof BigDecimal d) {
            return d.doubleValue();
        }
        return o instanceof Number n ? n.doubleValue() : 0d;
    }

    /** Legacy boolean flags are integers compared with 1 ({@code rs.getInt(...) == 1}); NULL is false. */
    boolean flag(String column) throws SQLException {
        Integer v = integer(column);
        return v != null && v == 1;
    }

    /** Flag with an explicit value for NULL (used where the legacy column has a non-zero DEFAULT). */
    boolean flag(String column, boolean whenNull) throws SQLException {
        Integer v = integer(column);
        return v == null ? whenNull : v == 1;
    }

    String string(String column) throws SQLException {
        Integer i = col(column);
        return i == null ? null : rs.getString(i);
    }

    /** Long text; binary columns (BLOB-like legacy definitions) are decoded as UTF-8. */
    String text(String column) throws SQLException {
        Object o = raw(column);
        if (o instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return string(column);
    }

    /** Trimmed string, blank becomes null (CHAR columns are blank padded). */
    String trimmed(String column) throws SQLException {
        String s = string(column);
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /** DATETIME columns are read as local wall time in the source zone; BIGINT columns as epoch seconds/millis. */
    Instant instant(String column) throws SQLException {
        Object o = raw(column);
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            long v = n.longValue();
            if (v <= 0) {
                return null;
            }
            return v > 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
        }
        if (o instanceof Timestamp ts) {
            return ts.toLocalDateTime().atZone(zone).toInstant();
        }
        if (o instanceof LocalDateTime ldt) {
            return ldt.atZone(zone).toInstant();
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate().atStartOfDay(zone).toInstant();
        }
        if (o instanceof LocalDate d) {
            return d.atStartOfDay(zone).toInstant();
        }
        if (o instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return rs.getObject(col(column), LocalDateTime.class).atZone(zone).toInstant();
    }
}
