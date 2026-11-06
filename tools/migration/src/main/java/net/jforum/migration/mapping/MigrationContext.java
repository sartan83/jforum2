package net.jforum.migration.mapping;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.jforum.migration.source.SourceDatabase;
import net.jforum.migration.target.BatchSink;
import net.jforum.migration.target.Target;

/**
 * What a {@link TableMigrator} needs: the source, the target and the run options.
 */
public class MigrationContext
{
	private final SourceDatabase source;
	private final Target target;
	private final int batchSize;
	private final Path attachmentsDir;
	private final List<String> warnings = new ArrayList<>();

	public MigrationContext(SourceDatabase source, Target target, int batchSize, Path attachmentsDir)
	{
		this.source = source;
		this.target = target;
		this.batchSize = batchSize;
		this.attachmentsDir = attachmentsDir;
	}

	public SourceDatabase source()
	{
		return this.source;
	}

	public Target target()
	{
		return this.target;
	}

	public Path attachmentsDir()
	{
		return this.attachmentsDir;
	}

	public BatchSink sink(String collection)
	{
		return new BatchSink(this.target, collection, this.batchSize);
	}

	public void warn(String message)
	{
		this.warnings.add(message);
	}

	public List<String> warnings()
	{
		return this.warnings;
	}
}
