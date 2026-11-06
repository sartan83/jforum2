package net.jforum.migration.mapping;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.jforum.migration.source.Columns;
import net.jforum.migration.source.SourceColumn;
import net.jforum.migration.target.BatchSink;
import net.jforum.migration.target.Measure;

/**
 * One table → one collection. With an id column, <code>_id</code> = that column (legacy primary key);
 * without (link/cache tables), <code>_id</code> = 1..n in the order of the non-LOB columns, so that
 * the documents still have the <code>int</code> id the application's documents expect.
 */
public class SimpleTableMigrator implements TableMigrator
{
	private final String table;
	private final String collection;
	private final String idColumn;
	private final FieldRules rules;

	public SimpleTableMigrator(String table, String collection, String idColumn)
	{
		this(table, collection, idColumn, FieldRules.NONE);
	}

	public SimpleTableMigrator(String table, String collection, String idColumn, FieldRules rules)
	{
		this.table = table;
		this.collection = collection;
		this.idColumn = idColumn;
		this.rules = rules;
	}

	public String idColumn()
	{
		return this.idColumn;
	}

	@Override
	public String collection()
	{
		return this.collection;
	}

	@Override
	public List<String> tables()
	{
		return List.of(this.table);
	}

	@Override
	public List<ParitySpec> parity(MigrationContext ctx)
	{
		return List.of(ParitySpec.table(this.table, this.collection, Measure.documents()));
	}

	@Override
	public long migrate(MigrationContext ctx) throws Exception
	{
		String orderBy;
		if (this.idColumn != null) {
			orderBy = "[" + this.idColumn + "]";
		}
		else {
			List<SourceColumn> columns = ctx.source().columns(this.table);
			orderBy = columns.stream().filter(c -> !c.isLob()).map(SourceColumn::quoted).collect(Collectors.joining(", "));
		}
		String sql = "SELECT * FROM " + ctx.source().qualified(this.table) + (orderBy.isEmpty() ? "" : " ORDER BY " + orderBy);
		int[] sequence = { 0 };
		try (BatchSink sink = ctx.sink(this.collection)) {
			ctx.source().stream(sql, row -> {
				var doc = Columns.toDocument(row, this.idColumn, Set.of());
				if (this.idColumn == null) {
					doc.put("_id", ++sequence[0]);
				}
				sink.add(this.rules.apply(doc));
			});
			sink.flush();
			return sink.written();
		}
	}
}
