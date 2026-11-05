package net.jforum.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/** Runs the migration on startup (unless {@code migration.enabled=false}) and prints the counts report. */
@Component
class MigrationRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final SqlServerToMongoMigration migration;
    private final MigrationProperties properties;
    private int exitCode;

    MigrationRunner(SqlServerToMongoMigration migration, MigrationProperties properties) {
        this.migration = migration;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.isEnabled()) {
            return;
        }
        MigrationReport report = migration.migrate();
        log.info(format(report));
        if (!report.isDryRun() && !report.isVerified() && properties.isFailOnMismatch()) {
            log.error("Verification failed: {} mismatch(es)", report.getMismatches().size());
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    static String format(MigrationReport report) {
        StringBuilder sb = new StringBuilder(System.lineSeparator());
        sb.append(report.isDryRun() ? "DRY RUN - nothing was written" : "Migration report").append(System.lineSeparator());
        sb.append(String.format("%-58s %-18s %10s %10s%n", "source", "collection", "rows",
            report.isDryRun() ? "would write" : "documents"));
        for (MigrationReport.TableResult t : report.getTables()) {
            sb.append(String.format("%-58s %-18s %10d %10d%n", t.source(), t.target(), t.rows(), t.documents()));
        }
        for (String skipped : report.getSkippedTables()) {
            sb.append(String.format("%-58s %-18s%n", skipped, "(table not found, skipped)"));
        }
        sb.append(String.format("attachment files: %d found, %d missing%n", report.getAttachmentFilesStored(),
            report.getAttachmentFilesMissing()));
        if (!report.isDryRun()) {
            sb.append("counters: ").append(report.getCounters()).append(System.lineSeparator());
            sb.append(String.format("verification: %d checks, %d mismatch(es) -> %s%n", report.getChecks().size(),
                report.getMismatches().size(), report.isVerified() ? "OK" : "FAILED"));
        }
        return sb.toString();
    }
}
