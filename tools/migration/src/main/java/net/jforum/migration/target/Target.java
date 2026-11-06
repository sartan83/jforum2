package net.jforum.migration.target;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.bson.Document;

/**
 * Destination of the migration: MongoDB ({@link MongoTarget}) or an in-memory
 * counter used by <code>--dry-run</code> ({@link DryRunTarget}).
 */
public interface Target extends AutoCloseable
{
	String GRIDFS_BUCKET = "fs";

	/** Drops the given collections (and the GridFS bucket when <code>gridFs</code>). */
	void drop(Collection<String> collections, boolean gridFs);

	/** Upserts the documents by <code>_id</code> (re-runs do not create duplicates). */
	void write(String collection, List<Document> documents);

	/** Stores a file in the GridFS bucket <code>fs</code>, replacing a previous copy with the same metadata.attachId/thumb. */
	void uploadFile(String filename, Path file, Document metadata);

	long measure(String collection, Measure measure);

	/** Highest numeric <code>_id</code> of the collection, 0 when empty. */
	long maxId(String collection);

	/**
	 * Sets <code>sequences.{_id: collection}.value</code>: to <code>value</code> when <code>exact</code>,
	 * otherwise to max(current, value).
	 */
	void initSequence(String collection, long value, boolean exact);

	/** Current value of the sequence, null when missing. */
	Long sequence(String collection);

	@Override
	void close();
}
