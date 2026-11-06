package net.jforum.migration.source;

import java.sql.Types;

/**
 * A column of a legacy table. <code>name</code> is the raw SQL name (some legacy columns have
 * trailing spaces, e.g. <code>[description ]</code>), <code>key</code> the trimmed lower case name.
 */
public record SourceColumn(String name, int jdbcType)
{
	public String key()
	{
		return Columns.key(this.name);
	}

	/** text/ntext/image columns cannot be used in ORDER BY on SQL Server. */
	public boolean isLob()
	{
		return switch (this.jdbcType) {
			case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.LONGVARBINARY, Types.CLOB, Types.NCLOB,
				Types.BLOB, Types.SQLXML -> true;
			default -> false;
		};
	}

	public String quoted()
	{
		return "[" + this.name.replace("]", "]]") + "]";
	}
}
