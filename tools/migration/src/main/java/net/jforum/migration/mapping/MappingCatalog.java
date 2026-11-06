package net.jforum.migration.mapping;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jforum.migration.source.SourceColumn;
import net.jforum.migration.source.SourceDatabase;

/**
 * The table → collection mapping (documented in tools/migration/README.md).
 * Grouped tables are handled by dedicated migrators, every other <code>jforum_*</code> table found in the
 * source becomes its own collection (name without the <code>jforum_</code> prefix).
 */
public final class MappingCatalog
{
	/** Tables whose rows have no usable single-column key: _id is generated (1..n). */
	public static final Set<String> GENERATED_ID_TABLES = Set.of(
		"jforum_topics_watch",
		// the SQL Server DDL declares forum_id as IDENTITY primary key, which is a schema bug
		// (one row per watcher); it is migrated as the link table it is in every other database
		"jforum_forums_watch",
		"jforum_sessions",
		"jforum_search_results",
		"jforum_search_topics",
		"jforum_search_wordmatch");

	/** Known single-table mappings: table → legacy primary key column. */
	public static final Map<String, String> PRIMARY_KEYS = new LinkedHashMap<>();

	static {
		PRIMARY_KEYS.put("jforum_categories", "categories_id");
		PRIMARY_KEYS.put("jforum_forums", "forum_id");
		PRIMARY_KEYS.put("jforum_topics", "topic_id");
		PRIMARY_KEYS.put("jforum_groups", "group_id");
		PRIMARY_KEYS.put("jforum_ranks", "rank_id");
		PRIMARY_KEYS.put("jforum_smilies", "smilie_id");
		PRIMARY_KEYS.put("jforum_themes", "themes_id");
		PRIMARY_KEYS.put("jforum_words", "word_id");
		PRIMARY_KEYS.put("jforum_karma", "karma_id");
		PRIMARY_KEYS.put("jforum_bookmarks", "bookmark_id");
		PRIMARY_KEYS.put("jforum_config", "config_id");
		PRIMARY_KEYS.put("jforum_quota_limit", "quota_limit_id");
		PRIMARY_KEYS.put("jforum_extension_groups", "extension_group_id");
		PRIMARY_KEYS.put("jforum_extensions", "extension_id");
		PRIMARY_KEYS.put("jforum_attach_quota", "attach_quota_id");
		PRIMARY_KEYS.put("jforum_banlist", "banlist_id");
		PRIMARY_KEYS.put("jforum_search_words", "word_id");
		PRIMARY_KEYS.put("jforum_moderation_log", "log_id");
		PRIMARY_KEYS.put("jforum_banner", "banner_id");
		PRIMARY_KEYS.put("jforum_api", "api_id");
	}

	private MappingCatalog() {}

	/** The grouped (multi-table) migrators. */
	public static List<TableMigrator> groups()
	{
		return List.of(
			new UsersMigrator(),
			TextMergeMigrator.posts(),
			TextMergeMigrator.privateMessages(),
			new RolesMigrator(),
			new PollsMigrator(),
			new AttachmentsMigrator());
	}

	/** Every table the tool knows by name (expected in a JForum 2.1.8 database). */
	public static Set<String> knownTables()
	{
		Set<String> known = new HashSet<>(PRIMARY_KEYS.keySet());
		known.addAll(GENERATED_ID_TABLES);
		groups().forEach(m -> known.addAll(m.tables()));
		return known;
	}

	public static String collectionFor(String table)
	{
		return table.startsWith("jforum_") ? table.substring("jforum_".length()) : table;
	}

	/**
	 * Builds the migrators for the tables present in the source; groups whose main table is missing are
	 * skipped and reported through <code>missing</code>.
	 */
	public static List<TableMigrator> build(SourceDatabase source, List<String> missing) throws SQLException
	{
		List<TableMigrator> migrators = new ArrayList<>();
		Set<String> claimed = new HashSet<>();
		for (TableMigrator group : groups()) {
			claimed.addAll(group.tables());
			if (source.exists(group.main())) {
				migrators.add(group);
			}
			else {
				missing.add(group.main());
			}
		}
		Set<String> singles = new java.util.TreeSet<>(PRIMARY_KEYS.keySet());
		singles.addAll(GENERATED_ID_TABLES);
		singles.addAll(source.tables());
		singles.removeAll(claimed);
		for (String table : singles) {
			if (!source.exists(table)) {
				missing.add(table);
				continue;
			}
			String id = GENERATED_ID_TABLES.contains(table) ? null
				: PRIMARY_KEYS.containsKey(table) ? PRIMARY_KEYS.get(table) : detectIntegerKey(source, table);
			migrators.add(new SimpleTableMigrator(table, collectionFor(table), id));
		}
		return migrators;
	}

	/** Single-column integer primary key of an unknown table, or null. */
	private static String detectIntegerKey(SourceDatabase source, String table) throws SQLException
	{
		List<String> pk = source.primaryKey(table);
		if (pk.size() != 1) {
			return null;
		}
		for (SourceColumn c : source.columns(table)) {
			if (c.name().equalsIgnoreCase(pk.get(0))) {
				return switch (c.jdbcType()) {
					case Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> c.name();
					default -> null;
				};
			}
		}
		return null;
	}
}
