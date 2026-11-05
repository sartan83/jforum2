package net.jforum.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code migration.*} options (command line: {@code --migration.drop-target=true}, ...). */
@ConfigurationProperties("migration")
public class MigrationProperties {

    /** Run the migration on startup (the tests disable it and call {@link SqlServerToMongoMigration} directly). */
    private boolean enabled = true;

    /** Only read the source and report what would be migrated; nothing is written to MongoDB. */
    private boolean dryRun;

    /** Drop the target collections (and the GridFS bucket and counters) before importing. */
    private boolean dropTarget;

    /** Number of documents per MongoDB bulk write, also used as JDBC fetch size. */
    private int batchSize = 500;

    /** Legacy attachments directory (legacy {@code attachments.store.dir}, e.g. {@code <webapp>/upload}). */
    private String uploadDir;

    /** GridFS bucket receiving the attachment bytes. */
    private String gridfsBucket = "fs";

    /** Time zone of the legacy DATETIME values (SQL Server stores them without zone). */
    private String sourceTimezone = "UTC";

    /** Exit with a non-zero status when the final verification finds a mismatch. */
    private boolean failOnMismatch = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isDropTarget() {
        return dropTarget;
    }

    public void setDropTarget(boolean dropTarget) {
        this.dropTarget = dropTarget;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getGridfsBucket() {
        return gridfsBucket;
    }

    public void setGridfsBucket(String gridfsBucket) {
        this.gridfsBucket = gridfsBucket;
    }

    public String getSourceTimezone() {
        return sourceTimezone;
    }

    public void setSourceTimezone(String sourceTimezone) {
        this.sourceTimezone = sourceTimezone;
    }

    public boolean isFailOnMismatch() {
        return failOnMismatch;
    }

    public void setFailOnMismatch(boolean failOnMismatch) {
        this.failOnMismatch = failOnMismatch;
    }
}
