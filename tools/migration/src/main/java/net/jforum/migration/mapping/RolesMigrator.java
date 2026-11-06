package net.jforum.migration.mapping;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;

import net.jforum.migration.source.Columns;
import net.jforum.migration.target.BatchSink;
import net.jforum.migration.target.Measure;

/**
 * jforum_roles + jforum_role_values → <code>roles</code> with embedded
 * <code>values: [{roleValue, roleType}]</code> (ordered by role_value).
 */
public class RolesMigrator implements TableMigrator
{
	@Override
	public String collection()
	{
		return "roles";
	}

	@Override
	public List<String> tables()
	{
		return List.of("jforum_roles", "jforum_role_values");
	}

	@Override
	public List<ParitySpec> parity(MigrationContext ctx)
	{
		return List.of(
			ParitySpec.table("jforum_roles", "roles", Measure.documents()),
			ParitySpec.table("jforum_role_values", "roles", Measure.arraySize("values")));
	}

	@Override
	public long migrate(MigrationContext ctx) throws Exception
	{
		EmbeddedChildren.warnIfMissing(ctx, "jforum_role_values", "roles.values");
		Map<Object, List<Document>> values = EmbeddedChildren.load(ctx.source(), "jforum_role_values", "role_id",
			"role_id, role_value");
		try (BatchSink sink = ctx.sink("roles")) {
			ctx.source().stream("SELECT * FROM " + ctx.source().qualified("jforum_roles") + " ORDER BY role_id", row -> {
				var doc = Columns.toDocument(row, "role_id", Set.of());
				doc.put("values", values.getOrDefault(row.get("role_id"), List.of()));
				sink.add(doc);
			});
			sink.flush();
			return sink.written();
		}
	}
}
