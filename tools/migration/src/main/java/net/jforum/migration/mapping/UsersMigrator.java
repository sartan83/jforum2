package net.jforum.migration.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.jforum.migration.source.Columns;
import net.jforum.migration.target.BatchSink;
import net.jforum.migration.target.Measure;

/**
 * jforum_users + jforum_user_groups → <code>users</code> with the sorted, distinct <code>groupIds</code>.
 * Memberships of user ids that do not exist in jforum_users are reported as a mismatch.
 */
public class UsersMigrator implements TableMigrator
{
	private Long distinctMemberships;

	@Override
	public String collection()
	{
		return "users";
	}

	@Override
	public List<String> tables()
	{
		return List.of("jforum_users", "jforum_user_groups");
	}

	@Override
	public List<ParitySpec> parity(MigrationContext ctx)
	{
		return List.of(
			ParitySpec.table("jforum_users", "users", Measure.documents()),
			this.distinctMemberships == null
				? ParitySpec.table("jforum_user_groups", "users", Measure.arraySize("groupIds"))
				: new ParitySpec("jforum_user_groups", "users", Measure.arraySize("groupIds"), this.distinctMemberships));
	}

	@Override
	public long migrate(MigrationContext ctx) throws Exception
	{
		EmbeddedChildren.warnIfMissing(ctx, "jforum_user_groups", "users.groupIds");
		Map<Object, TreeSet<Integer>> groups = new HashMap<>();
		if (ctx.source().exists("jforum_user_groups")) {
			ctx.source().stream("SELECT user_id, group_id FROM " + ctx.source().qualified("jforum_user_groups")
				+ " ORDER BY user_id, group_id", row -> groups.computeIfAbsent(row.get("user_id"), k -> new TreeSet<>())
					.add(((Number)row.get("group_id")).intValue()));
		}
		this.distinctMemberships = groups.values().stream().mapToLong(TreeSet::size).sum();
		long rows = ctx.source().exists("jforum_user_groups") ? ctx.source().count("jforum_user_groups") : 0;
		if (rows != this.distinctMemberships) {
			ctx.warn("jforum_user_groups: " + (rows - this.distinctMemberships)
				+ " duplicate (user_id, group_id) row(s) collapsed; parity compares the distinct memberships");
		}
		try (BatchSink sink = ctx.sink("users")) {
			ctx.source().stream("SELECT * FROM " + ctx.source().qualified("jforum_users") + " ORDER BY user_id", row -> {
				var doc = Columns.toDocument(row, "user_id", Set.of());
				doc.put("groupIds", new ArrayList<>(groups.getOrDefault(row.get("user_id"), new TreeSet<>())));
				sink.add(doc);
			});
			sink.flush();
			return sink.written();
		}
	}
}
