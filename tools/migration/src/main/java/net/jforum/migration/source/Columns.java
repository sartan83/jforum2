package net.jforum.migration.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;

/**
 * Column name and value conversion rules shared by every table mapping:
 * <ul>
 * <li>field name = camelCase of the column name (<code>topic_first_post_id</code> → <code>topicFirstPostId</code>)</li>
 * <li>integer types → int32 (bigint → int32 when it fits, int64 otherwise), decimal/float → double,
 *     datetime → BSON date, char(n) → right-trimmed string, binary → BSON binary, anything else → string</li>
 * <li>SQL NULL → field omitted</li>
 * </ul>
 */
public final class Columns
{
	private Columns() {}

	public static String key(String column)
	{
		return column.trim().toLowerCase(Locale.ROOT);
	}

	public static String camelCase(String column)
	{
		String[] parts = key(column).split("_+");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.isEmpty()) {
				sb.append(part);
			}
			else {
				sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
			}
		}
		return sb.toString();
	}

	/**
	 * Converts a row to a document: <code>idColumn</code> (may be null) becomes <code>_id</code>,
	 * <code>excluded</code> columns are skipped, the others become camelCase fields.
	 */
	public static Document toDocument(Map<String, Object> row, String idColumn, Collection<String> excluded)
	{
		Document doc = new Document();
		if (idColumn != null) {
			doc.put("_id", row.get(key(idColumn)));
		}
		for (Map.Entry<String, Object> e : row.entrySet()) {
			String column = e.getKey();
			if (column.equals(idColumn == null ? null : key(idColumn)) || excluded.contains(column)) {
				continue;
			}
			if (e.getValue() != null) {
				doc.put(camelCase(column), e.getValue());
			}
		}
		return doc;
	}

	public static Object read(ResultSet rs, int index, int jdbcType, Calendar calendar) throws SQLException
	{
		switch (jdbcType) {
			case Types.BIGINT: {
				long v = rs.getLong(index);
				if (rs.wasNull()) {
					return null;
				}
				if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
					return Integer.valueOf((int)v);
				}
				return Long.valueOf(v);
			}
			case Types.INTEGER:
			case Types.SMALLINT:
			case Types.TINYINT: {
				int v = rs.getInt(index);
				return rs.wasNull() ? null : Integer.valueOf(v);
			}
			case Types.BIT:
			case Types.BOOLEAN: {
				boolean v = rs.getBoolean(index);
				return rs.wasNull() ? null : Boolean.valueOf(v);
			}
			case Types.DECIMAL:
			case Types.NUMERIC:
			case Types.FLOAT:
			case Types.REAL:
			case Types.DOUBLE: {
				double v = rs.getDouble(index);
				return rs.wasNull() ? null : Double.valueOf(v);
			}
			case Types.DATE:
			case Types.TIME:
			case Types.TIMESTAMP:
			case Types.TIMESTAMP_WITH_TIMEZONE:
			case -155: { // microsoft.sql.Types.DATETIMEOFFSET
				Timestamp ts = rs.getTimestamp(index, calendar);
				return ts == null ? null : new Date(ts.getTime());
			}
			case Types.CHAR:
			case Types.NCHAR: {
				String v = rs.getString(index);
				return v == null ? null : v.stripTrailing();
			}
			case Types.BINARY:
			case Types.VARBINARY:
			case Types.LONGVARBINARY:
			case Types.BLOB:
				return rs.getBytes(index);
			default:
				return rs.getString(index);
		}
	}
}
