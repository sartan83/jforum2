package net.jforum.migration.mapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.jforum.migration.source.Columns;
import net.jforum.migration.target.BatchSink;
import net.jforum.migration.target.Measure;

/**
 * A main table plus its 1:1 "_text" table merged into one document (LEFT JOIN on the primary key).
 * The text fields are always present (possibly null) when the text row exists, which is what the
 * parity check counts.
 * <ul>
 * <li>jforum_posts + jforum_posts_text → <code>posts</code> (post_subject → <code>subject</code>, post_text → <code>text</code>)</li>
 * <li>jforum_privmsgs + jforum_privmsgs_text → <code>privmsgs</code> (fields without the <code>privmsgs</code> prefix, see {@link MappingCatalog#PRIVMSGS_RULES})</li>
 * </ul>
 */
public class TextMergeMigrator implements TableMigrator
{
	private static final String MARKER = "migration_text_id";

	private final String table;
	private final String textTable;
	private final String collection;
	private final String idColumn;
	/** text column → document field */
	private final Map<String, String> textFields;
	private final FieldRules rules;

	public TextMergeMigrator(String table, String textTable, String collection, String idColumn, Map<String, String> textFields)
	{
		this(table, textTable, collection, idColumn, textFields, FieldRules.NONE);
	}

	public TextMergeMigrator(String table, String textTable, String collection, String idColumn, Map<String, String> textFields,
		FieldRules rules)
	{
		this.rules = rules;
		this.table = table;
		this.textTable = textTable;
		this.collection = collection;
		this.idColumn = idColumn;
		this.textFields = new LinkedHashMap<>(textFields);
	}

	public static TextMergeMigrator posts()
	{
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("post_subject", "subject");
		fields.put("post_text", "text");
		return new TextMergeMigrator("jforum_posts", "jforum_posts_text", "posts", "post_id", fields);
	}

	public static TextMergeMigrator privateMessages()
	{
		return new TextMergeMigrator("jforum_privmsgs", "jforum_privmsgs_text", "privmsgs", "privmsgs_id",
			Map.of("privmsgs_text", "text"), MappingCatalog.PRIVMSGS_RULES);
	}

	/** Field used to count the merged text rows. */
	private String markerField()
	{
		return this.textFields.values().iterator().next();
	}

	@Override
	public String collection()
	{
		return this.collection;
	}

	@Override
	public List<String> tables()
	{
		return List.of(this.table, this.textTable);
	}

	@Override
	public List<ParitySpec> parity(MigrationContext ctx)
	{
		return List.of(
			ParitySpec.table(this.table, this.collection, Measure.documents()),
			ParitySpec.table(this.textTable, this.collection, Measure.fieldExists(markerField())));
	}

	@Override
	public long migrate(MigrationContext ctx) throws Exception
	{
		String sql;
		boolean hasText = ctx.source().exists(this.textTable);
		if (hasText) {
			String textColumns = this.textFields.keySet().stream().map(c -> "t.[" + c + "]").collect(Collectors.joining(", "));
			sql = "SELECT m.*, t.[" + this.idColumn + "] AS " + MARKER + ", " + textColumns
				+ " FROM " + ctx.source().qualified(this.table) + " m LEFT JOIN " + ctx.source().qualified(this.textTable)
				+ " t ON t.[" + this.idColumn + "] = m.[" + this.idColumn + "] ORDER BY m.[" + this.idColumn + "]";
		}
		else {
			EmbeddedChildren.warnIfMissing(ctx, this.textTable, this.collection + " text fields");
			sql = "SELECT * FROM " + ctx.source().qualified(this.table) + " ORDER BY [" + this.idColumn + "]";
		}
		Set<String> excluded = new java.util.HashSet<>(this.textFields.keySet());
		excluded.add(MARKER);
		try (BatchSink sink = ctx.sink(this.collection)) {
			ctx.source().stream(sql, row -> {
				var doc = Columns.toDocument(row, this.idColumn, excluded);
				if (row.get(MARKER) != null) {
					this.textFields.forEach((column, field) -> doc.put(field, row.get(column)));
				}
				sink.add(this.rules.apply(doc));
			});
			sink.flush();
			return sink.written();
		}
	}
}
