package net.jforum.migration.mapping;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;

import net.jforum.migration.source.Columns;
import net.jforum.migration.target.BatchSink;
import net.jforum.migration.target.Measure;

/**
 * jforum_vote_desc + jforum_vote_results + jforum_vote_voters → <code>polls</code> (_id = vote_id) with
 * <code>options: [{voteOptionId, voteOptionText, voteResult}]</code> ordered by option id and
 * <code>voters: [{voteUserId, voteUserIp}]</code>.
 */
public class PollsMigrator implements TableMigrator
{
	@Override
	public String collection()
	{
		return "polls";
	}

	@Override
	public List<String> tables()
	{
		return List.of("jforum_vote_desc", "jforum_vote_results", "jforum_vote_voters");
	}

	@Override
	public List<ParitySpec> parity(MigrationContext ctx)
	{
		return List.of(
			ParitySpec.table("jforum_vote_desc", "polls", Measure.documents()),
			ParitySpec.table("jforum_vote_results", "polls", Measure.arraySize("options")),
			ParitySpec.table("jforum_vote_voters", "polls", Measure.arraySize("voters")));
	}

	@Override
	public long migrate(MigrationContext ctx) throws Exception
	{
		EmbeddedChildren.warnIfMissing(ctx, "jforum_vote_results", "polls.options");
		EmbeddedChildren.warnIfMissing(ctx, "jforum_vote_voters", "polls.voters");
		Map<Object, List<Document>> options = EmbeddedChildren.load(ctx.source(), "jforum_vote_results", "vote_id",
			"vote_id, vote_option_id");
		Map<Object, List<Document>> voters = EmbeddedChildren.load(ctx.source(), "jforum_vote_voters", "vote_id",
			"vote_id, vote_user_id");
		try (BatchSink sink = ctx.sink("polls")) {
			ctx.source().stream("SELECT * FROM " + ctx.source().qualified("jforum_vote_desc") + " ORDER BY vote_id", row -> {
				var doc = Columns.toDocument(row, "vote_id", Set.of());
				doc.put("options", options.getOrDefault(row.get("vote_id"), List.of()));
				doc.put("voters", voters.getOrDefault(row.get("vote_id"), List.of()));
				sink.add(doc);
			});
			sink.flush();
			return sink.written();
		}
	}
}
