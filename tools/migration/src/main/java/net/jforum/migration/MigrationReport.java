package net.jforum.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Outcome of a migration run: per-table counts, counters, attachment files and the final verification. */
public class MigrationReport {

    /** One source table (or joined group of tables) migrated into one collection. */
    public record TableResult(String source, String target, long rows, long documents, long millis) {
    }

    /** One verification check: expected (from SQL) vs actual (in MongoDB). */
    public record Check(String source, String target, long expected, long actual) {
        public boolean ok() {
            return expected == actual;
        }
    }

    private final boolean dryRun;
    private final List<TableResult> tables = new ArrayList<>();
    private final List<String> skippedTables = new ArrayList<>();
    private final List<Check> checks = new ArrayList<>();
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private long attachmentFilesStored;
    private long attachmentFilesMissing;

    public MigrationReport(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public List<TableResult> getTables() {
        return tables;
    }

    public List<String> getSkippedTables() {
        return skippedTables;
    }

    public List<Check> getChecks() {
        return checks;
    }

    public Map<String, Integer> getCounters() {
        return counters;
    }

    public long getAttachmentFilesStored() {
        return attachmentFilesStored;
    }

    public long getAttachmentFilesMissing() {
        return attachmentFilesMissing;
    }

    void attachmentFileStored() {
        attachmentFilesStored++;
    }

    void attachmentFileMissing() {
        attachmentFilesMissing++;
    }

    public TableResult table(String source) {
        return tables.stream().filter(t -> t.source().equals(source)).findFirst().orElse(null);
    }

    public boolean isVerified() {
        return checks.stream().allMatch(Check::ok);
    }

    public List<Check> getMismatches() {
        return checks.stream().filter(c -> !c.ok()).toList();
    }
}
