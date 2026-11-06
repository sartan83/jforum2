package net.jforum.migration.report;

/**
 * <code>sequences.{_id: collection}.value</code> must be at least the highest migrated <code>_id</code>.
 */
public record SequenceEntry(String collection, long maxId, Long value, boolean ok)
{
}
