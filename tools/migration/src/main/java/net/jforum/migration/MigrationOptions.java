package net.jforum.migration;

import java.nio.file.Path;
import java.util.TimeZone;

import org.springframework.core.env.Environment;

import com.mongodb.ConnectionString;

/**
 * Command line options (also readable from application properties or environment variables,
 * e.g. <code>SOURCE_URL</code>, <code>TARGET_URI</code>, <code>DROP_TARGET</code>).
 */
public record MigrationOptions(
	String sourceUrl,
	String sourceUser,
	String sourcePassword,
	String sourceSchema,
	TimeZone sourceTimeZone,
	String targetUri,
	String targetDatabase,
	boolean dropTarget,
	boolean dryRun,
	int batchSize,
	Path attachmentsDir,
	Path reportDir)
{
	public static final String DEFAULT_DATABASE = "jforum";

	public static MigrationOptions from(Environment env)
	{
		String sourceUrl = required(env, "source.url");
		String user = first(env, "source.user", "source.username");
		String password = env.getProperty("source.password");
		String schema = env.getProperty("source.schema", "dbo");
		String tz = env.getProperty("source.timezone");
		TimeZone zone = tz == null || tz.isBlank() ? TimeZone.getDefault() : TimeZone.getTimeZone(tz);

		boolean dryRun = flag(env, "dry-run");
		String targetUri = env.getProperty("target.uri");
		if (!dryRun && (targetUri == null || targetUri.isBlank())) {
			throw new IllegalArgumentException("Missing --target.uri=mongodb://... (or use --dry-run)");
		}
		String database = env.getProperty("target.database");
		if ((database == null || database.isBlank()) && targetUri != null && !targetUri.isBlank()) {
			database = new ConnectionString(targetUri).getDatabase();
		}
		if (database == null || database.isBlank()) {
			database = DEFAULT_DATABASE;
		}

		int batchSize = Integer.parseInt(env.getProperty("batch-size", "1000"));
		if (batchSize < 1) {
			throw new IllegalArgumentException("--batch-size must be >= 1");
		}
		String attachments = env.getProperty("attachments-dir");
		Path attachmentsDir = attachments == null || attachments.isBlank() ? null : Path.of(attachments);
		Path reportDir = Path.of(env.getProperty("report-dir", "migration-report"));

		return new MigrationOptions(sourceUrl, user, password, schema, zone, targetUri, database,
			flag(env, "drop-target"), dryRun, batchSize, attachmentsDir, reportDir);
	}

	/** Target URI without credentials, safe to print in logs and reports. */
	public String redactedTargetUri()
	{
		return this.targetUri == null ? null : this.targetUri.replaceFirst("//[^/@]*@", "//***@");
	}

	/** Source JDBC URL without password properties. */
	public String redactedSourceUrl()
	{
		return this.sourceUrl.replaceAll("(?i)(password|pwd)=[^;]*", "$1=***");
	}

	private static String required(Environment env, String key)
	{
		String value = env.getProperty(key);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing --" + key);
		}
		return value;
	}

	private static String first(Environment env, String... keys)
	{
		for (String key : keys) {
			String value = env.getProperty(key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	/** <code>--flag</code>, <code>--flag=true</code> and <code>--flag=false</code> are accepted. */
	static boolean flag(Environment env, String key)
	{
		String value = env.getProperty(key);
		if (value == null) {
			return false;
		}
		return value.isBlank() || Boolean.parseBoolean(value.trim());
	}
}
