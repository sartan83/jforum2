package net.jforum.migration.source;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import net.jforum.migration.MigrationOptions;

/**
 * Read-only JDBC access to the legacy SQL Server database. Rows are streamed (forward only,
 * read only, <code>fetchSize = batch size</code>) and exposed as ordered maps keyed by the
 * trimmed lower case column name.
 */
public class SourceDatabase implements AutoCloseable
{
	@FunctionalInterface
	public interface RowHandler
	{
		void handle(Map<String, Object> row) throws Exception;
	}

	private final Connection connection;
	private final String schema;
	private final int fetchSize;
	private final Calendar calendar;
	private final Map<String, String> tables = new TreeMap<>();
	private final Map<String, Long> counts = new HashMap<>();

	public SourceDatabase(MigrationOptions options) throws SQLException
	{
		this.connection = DriverManager.getConnection(options.sourceUrl(), options.sourceUser(), options.sourcePassword());
		this.connection.setReadOnly(true);
		this.schema = options.sourceSchema();
		this.fetchSize = options.batchSize();
		this.calendar = Calendar.getInstance(options.sourceTimeZone());

		try (PreparedStatement ps = this.connection.prepareStatement(
			"SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' "
				+ "AND TABLE_SCHEMA = ? AND TABLE_NAME LIKE 'jforum[_]%'")) {
			ps.setString(1, this.schema);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					String name = rs.getString(1);
					this.tables.put(name.toLowerCase(Locale.ROOT), name);
				}
			}
		}
	}

	/** Lower case names of every <code>jforum_*</code> table of the source schema. */
	public List<String> tables()
	{
		return new ArrayList<>(this.tables.keySet());
	}

	public boolean exists(String table)
	{
		return this.tables.containsKey(table.toLowerCase(Locale.ROOT));
	}

	/** Schema-qualified, bracket-quoted table name. */
	public String qualified(String table)
	{
		String name = this.tables.getOrDefault(table.toLowerCase(Locale.ROOT), table);
		return "[" + this.schema + "].[" + name + "]";
	}

	public long count(String table) throws SQLException
	{
		String key = table.toLowerCase(Locale.ROOT);
		Long cached = this.counts.get(key);
		if (cached != null) {
			return cached;
		}
		if (!exists(table)) {
			return 0;
		}
		try (Statement st = this.connection.createStatement();
			ResultSet rs = st.executeQuery("SELECT COUNT_BIG(*) FROM " + qualified(table))) {
			rs.next();
			long n = rs.getLong(1);
			this.counts.put(key, n);
			return n;
		}
	}

	public List<SourceColumn> columns(String table) throws SQLException
	{
		List<SourceColumn> columns = new ArrayList<>();
		DatabaseMetaData md = this.connection.getMetaData();
		try (ResultSet rs = md.getColumns(this.connection.getCatalog(), this.schema, this.tables.getOrDefault(table.toLowerCase(Locale.ROOT), table), null)) {
			while (rs.next()) {
				columns.add(new SourceColumn(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE")));
			}
		}
		return columns;
	}

	/** Primary key columns (raw names) in key order. */
	public List<String> primaryKey(String table) throws SQLException
	{
		Map<Integer, String> keys = new TreeMap<>();
		DatabaseMetaData md = this.connection.getMetaData();
		try (ResultSet rs = md.getPrimaryKeys(this.connection.getCatalog(), this.schema, this.tables.getOrDefault(table.toLowerCase(Locale.ROOT), table))) {
			while (rs.next()) {
				keys.put(rs.getInt("KEY_SEQ"), rs.getString("COLUMN_NAME"));
			}
		}
		return new ArrayList<>(keys.values());
	}

	public void stream(String sql, RowHandler handler) throws Exception
	{
		try (Statement st = this.connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
			st.setFetchSize(this.fetchSize);
			try (ResultSet rs = st.executeQuery(sql)) {
				ResultSetMetaData md = rs.getMetaData();
				int n = md.getColumnCount();
				String[] keys = new String[n + 1];
				int[] types = new int[n + 1];
				for (int i = 1; i <= n; i++) {
					keys[i] = Columns.key(md.getColumnLabel(i));
					types[i] = md.getColumnType(i);
				}
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					for (int i = 1; i <= n; i++) {
						row.put(keys[i], Columns.read(rs, i, types[i], this.calendar));
					}
					handler.handle(row);
				}
			}
		}
	}

	@Override
	public void close() throws SQLException
	{
		this.connection.close();
	}
}
