package net.jforum.migration.target;

/**
 * What is counted on the target side of a parity check.
 */
public record Measure(Kind kind, String field)
{
	public enum Kind
	{
		/** number of documents of the collection */
		DOCUMENTS,
		/** number of documents having <code>field</code> (merged 1:1 child tables) */
		FIELD_EXISTS,
		/** sum of the sizes of the array <code>field</code> (embedded 1:n child tables) */
		ARRAY_SIZE,
		/** number of GridFS files (bucket <code>fs</code>) having <code>metadata.attachId</code>, thumbnails excluded */
		GRIDFS_FILES
	}

	public static Measure documents()
	{
		return new Measure(Kind.DOCUMENTS, null);
	}

	public static Measure fieldExists(String field)
	{
		return new Measure(Kind.FIELD_EXISTS, field);
	}

	public static Measure arraySize(String field)
	{
		return new Measure(Kind.ARRAY_SIZE, field);
	}

	public static Measure gridFsFiles()
	{
		return new Measure(Kind.GRIDFS_FILES, null);
	}

	public String describe()
	{
		return switch (this.kind) {
			case DOCUMENTS -> "documents";
			case FIELD_EXISTS -> "documents with " + this.field;
			case ARRAY_SIZE -> "embedded " + this.field + "[]";
			case GRIDFS_FILES -> "GridFS files";
		};
	}
}
