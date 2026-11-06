package net.jforum.migration.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;

import net.jforum.migration.source.Columns;
import net.jforum.migration.source.SourceDatabase;

/**
 * Loads a 1:n child table in memory, grouped by its parent key, as embedded documents
 * (camelCase fields, parent key column excluded).
 */
final class EmbeddedChildren
{
	private EmbeddedChildren() {}

	static Map<Object, List<Document>> load(SourceDatabase source, String table, String parentColumn, String orderBy)
		throws Exception
	{
		Map<Object, List<Document>> children = new HashMap<>();
		if (!source.exists(table)) {
			return children;
		}
		String parentKey = Columns.key(parentColumn);
		source.stream("SELECT * FROM " + source.qualified(table) + " ORDER BY " + orderBy, row -> {
			Object parent = row.get(parentKey);
			children.computeIfAbsent(parent, k -> new ArrayList<>()).add(Columns.toDocument(row, null, Set.of(parentKey)));
		});
		return children;
	}

	static void warnIfMissing(MigrationContext ctx, String table, String what)
	{
		if (!ctx.source().exists(table)) {
			ctx.warn(table + " not found in the source: " + what + " left empty");
		}
	}
}
