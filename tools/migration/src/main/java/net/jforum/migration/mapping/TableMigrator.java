package net.jforum.migration.mapping;

import java.util.List;

/**
 * Migrates a group of legacy tables into one MongoDB collection.
 */
public interface TableMigrator
{
	/** Target collection. */
	String collection();

	/** Legacy tables (lower case) read by this migrator; the first one is the main table. */
	List<String> tables();

	/** Parity checks of this migrator (called before and after {@link #migrate}). */
	List<ParitySpec> parity(MigrationContext ctx);

	/** Reads the source tables and writes the documents; returns the number of documents written. */
	long migrate(MigrationContext ctx) throws Exception;

	default String main()
	{
		return tables().get(0);
	}
}
