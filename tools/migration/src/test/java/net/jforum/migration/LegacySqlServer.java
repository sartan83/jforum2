package net.jforum.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * SQL Server 2022 container holding a legacy JForum database: the shipped {@code sqlserver_db_struct.sql}
 * (UTF-16) and {@code sqlserver_data_dump.sql}, plus the test fixtures in {@code src/test/resources/legacy}.
 */
final class LegacySqlServer {

    static final String DATABASE = "jforum";

    static final MSSQLServerContainer<?> CONTAINER = new MSSQLServerContainer<>(
        DockerImageName.parse(System.getProperty("mssql.image", "mcr.microsoft.com/mssql/server:2022-latest"))
            .asCompatibleSubstituteFor("mcr.microsoft.com/mssql/server"))
        .acceptLicense();

    private static boolean loaded;

    private LegacySqlServer() {
    }

    static synchronized void start() {
        if (loaded) {
            return;
        }
        CONTAINER.start();
        try (Connection master = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
                 CONTAINER.getPassword());
             Statement st = master.createStatement()) {
            st.execute("CREATE DATABASE " + DATABASE);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        Path dir = Path.of(System.getProperty("legacy.sqlserver.dir", "../../WEB-INF/config/database/sqlserver"));
        try (Connection con = connection(); Statement st = con.createStatement()) {
            for (String batch : schemaBatches(read(dir.resolve("sqlserver_db_struct.sql"), StandardCharsets.UTF_16))) {
                st.execute(batch);
            }
            st.execute(resource("extra-tables.sql"));
            st.execute(read(dir.resolve("sqlserver_data_dump.sql"), StandardCharsets.UTF_8));
            st.execute(resource("extra-data.sql"));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load the legacy JForum schema/data", e);
        }
        loaded = true;
    }

    static String jdbcUrl() {
        return CONTAINER.getJdbcUrl() + ";databaseName=" + DATABASE;
    }

    static Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
    }

    static void execute(String sql) {
        try (Connection con = connection(); Statement st = con.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Splits the script on GO lines; the malformed jforum_moderation_log batch is replaced by a corrected DDL. */
    static List<String> schemaBatches(String script) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.replace("\uFEFF", "").split("\r?\n")) {
            if (line.trim().equalsIgnoreCase("GO")) {
                add(batches, current.toString());
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        add(batches, current.toString());
        return batches;
    }

    private static void add(List<String> batches, String batch) {
        if (batch.isBlank()) {
            return;
        }
        if (batch.contains("CREATE TABLE CREATE TABLE")) {
            batch = resource("moderation-log.sql");
        }
        batches.add(batch);
    }

    private static String read(Path file, Charset charset) {
        try {
            return Files.readString(file, charset);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String resource(String name) {
        try (InputStream in = LegacySqlServer.class.getResourceAsStream("/legacy/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
