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
 * <code>roleValues: [String]</code> (ordered by role_value), as <code>RoleDocument</code>.
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
			ParitySpec.table("jforum_role_values", "roles", Measure.arraySize("roleValues")));
	}

	@Override
	public long migrate(MigrationContext ctx) throws Exception
	{
		EmbeddedChildren.warnIfMissing(ctx, "jforum_role_values", "roles.values");
		Map<Object, List<Document>> values = EmbeddedChildren.load(ctx.source(), "jforum_role_values", "role_id",
			"role_id, role_value");
		long nullValues = values.values().stream().flatMap(List::stream).filter(v -> v.get("roleValue") == null).count();
		if (nullValues > 0) {
			ctx.warn("jforum_role_values: " + nullValues + " row(s) with a NULL role_value kept as null in roles.roleValues");
		}
		try (BatchSink sink = ctx.sink("roles")) {
			ctx.source().stream("SELECT * FROM " + ctx.source().qualified("jforum_roles") + " ORDER BY role_id", row -> {
				var doc = Columns.toDocument(row, "role_id", Set.of());
				doc.put("roleValues", values.getOrDefault(row.get("role_id"), List.of()).stream()
					.map(v -> v.get("roleValue") == null ? null : v.get("roleValue").toString()).toList());
				sink.add(doc);
			});
			sink.flush();
			return sink.written();
		}
	}
}
