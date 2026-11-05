package net.jforum.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

/** Read-only access to the legacy JForum database. */
@Component
class LegacySource {

    @FunctionalInterface
    interface RowHandler {
        void handle(SourceRow row) throws SQLException;
    }

    private final JdbcTemplate jdbc;
    private final MigrationProperties properties;
    private Set<String> tables;

    LegacySource(JdbcTemplate jdbc, MigrationProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** True when the table exists (some tables, e.g. jforum_banner / jforum_api, are missing in older SQL Server installs). */
    boolean exists(String table) {
        if (tables == null) {
            Set<String> names = new HashSet<>();
            jdbc.query("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'",
                (ResultSet rs) -> {
                    names.add(rs.getString(1).toLowerCase(Locale.ROOT));
                });
            tables = names;
        }
        return tables.contains(table.toLowerCase(Locale.ROOT));
    }

    void refresh() {
        tables = null;
    }

    long count(String sql) {
        Number n = jdbc.queryForObject(sql, Number.class);
        return n == null ? 0 : n.longValue();
    }

    /** Streams a query forward-only, {@code migration.batch-size} rows per round trip. */
    void stream(String sql, RowHandler handler) {
        ZoneId zone = ZoneId.of(properties.getSourceTimezone());
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(Math.max(1, properties.getBatchSize()));
            return ps;
        }, (ResultSetExtractor<Void>) rs -> {
            SourceRow row = new SourceRow(rs, zone);
            while (rs.next()) {
                handler.handle(row);
            }
            return null;
        });
    }
}
