package net.jforum.migration.mapping;

import net.jforum.migration.target.Measure;

/**
 * One line of the parity report: rows of <code>source</code> must equal <code>measure</code> on <code>collection</code>.
 * <code>expected</code> overrides the source row count (null = <code>COUNT(*)</code> of the source table).
 */
public record ParitySpec(String source, String collection, Measure measure, Long expected)
{
	public static ParitySpec table(String table, String collection, Measure measure)
	{
		return new ParitySpec(table, collection, measure, null);
	}
}
