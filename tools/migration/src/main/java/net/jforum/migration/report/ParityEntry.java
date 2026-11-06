package net.jforum.migration.report;

/**
 * Source rows of one table vs what was found in MongoDB.
 */
public record ParityEntry(String source, long sourceRows, String target, String measure, long targetCount, boolean match)
{
}
