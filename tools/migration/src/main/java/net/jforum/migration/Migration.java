package net.jforum.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import net.jforum.migration.mapping.MappingCatalog;
import net.jforum.migration.mapping.MigrationContext;
import net.jforum.migration.mapping.ParitySpec;
import net.jforum.migration.mapping.TableMigrator;
import net.jforum.migration.report.ParityEntry;
import net.jforum.migration.report.ParityReport;
import net.jforum.migration.report.SequenceEntry;
import net.jforum.migration.source.SourceDatabase;
import net.jforum.migration.target.DryRunTarget;
import net.jforum.migration.target.MongoTarget;
import net.jforum.migration.target.Target;

/**
 * Reads every jforum_* table, writes the documents, initializes the <code>sequences</code> collection and
 * builds the parity report.
 */
public class Migration
{
	public static final String JSON_REPORT = "parity-report.json";
	public static final String TEXT_REPORT = "parity-report.txt";

	private static final Logger LOG = LoggerFactory.getLogger(Migration.class);

	private final MigrationOptions options;

	public Migration(MigrationOptions options)
	{
		this.options = options;
	}

	public ParityReport run() throws Exception
	{
		ParityReport report = new ParityReport();
		report.source = this.options.redactedSourceUrl();
		report.target = this.options.dryRun() ? "(dry run)" : this.options.redactedTargetUri() + " [" + this.options.targetDatabase() + "]";
		report.dryRun = this.options.dryRun();
		report.startedAt = Instant.now();

		try (SourceDatabase source = new SourceDatabase(this.options);
			Target target = this.options.dryRun() ? new DryRunTarget()
				: new MongoTarget(this.options.targetUri(), this.options.targetDatabase())) {
			MigrationContext ctx = new MigrationContext(source, target, this.options.batchSize(), this.options.attachmentsDir());
			List<TableMigrator> migrators = MappingCatalog.build(source, report.missingTables);
			for (String table : report.missingTables) {
				LOG.warn("Table {} not found in the source, skipped", table);
			}

			Set<String> collections = new LinkedHashSet<>();
			migrators.forEach(m -> collections.add(m.collection()));
			if (target instanceof DryRunTarget dry) {
				for (TableMigrator m : migrators) {
					for (ParitySpec spec : m.parity(ctx)) {
						dry.track(spec.collection(), spec.measure());
					}
				}
			}
			if (this.options.dropTarget()) {
				LOG.info("Dropping target collections {}", collections);
				target.drop(collections, this.options.attachmentsDir() != null);
			}

			for (TableMigrator m : migrators) {
				long start = System.currentTimeMillis();
				long written = m.migrate(ctx);
				LOG.info("{} -> {}: {} documents in {} ms", m.tables(), m.collection(), written, System.currentTimeMillis() - start);
			}

			for (String collection : collections) {
				long max = target.maxId(collection);
				target.initSequence(collection, max, this.options.dropTarget());
				Long value = target.sequence(collection);
				report.sequences.add(new SequenceEntry(collection, max, value, value != null && value >= max));
			}

			for (TableMigrator m : migrators) {
				for (ParitySpec spec : m.parity(ctx)) {
					long expected = spec.expected() != null ? spec.expected() : source.count(spec.source());
					long actual = target.measure(spec.collection(), spec.measure());
					report.tables.add(new ParityEntry(spec.source(), expected, spec.collection(), spec.measure().describe(),
						actual, expected == actual));
				}
			}
			report.warnings.addAll(ctx.warnings());
		}
		report.finishedAt = Instant.now();
		write(report);
		return report;
	}

	private void write(ParityReport report) throws Exception
	{
		Path dir = this.options.reportDir();
		Files.createDirectories(dir);
		ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(SerializationFeature.INDENT_OUTPUT);
		json.writeValue(dir.resolve(JSON_REPORT).toFile(), report);
		String text = report.toText();
		Files.writeString(dir.resolve(TEXT_REPORT), text);
		LOG.info("Parity report written to {}\n{}", dir.toAbsolutePath(), text);
	}

}
