package net.jforum.migration.target;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

/**
 * <code>--dry-run</code>: nothing is written, the transformed documents are only counted
 * (with upsert semantics: one entry per distinct <code>_id</code>) for the measures registered
 * with {@link #track(String, Measure)}.
 */
public class DryRunTarget implements Target
{
	private final Map<String, List<Measure>> tracked = new HashMap<>();
	private final Map<String, Map<Object, long[]>> documents = new HashMap<>();
	private final Map<String, Long> sequences = new HashMap<>();
	private long files;

	public void track(String collection, Measure measure)
	{
		List<Measure> measures = this.tracked.computeIfAbsent(collection, c -> new ArrayList<>());
		if (!measures.contains(measure)) {
			measures.add(measure);
		}
	}

	@Override
	public void drop(Collection<String> collections, boolean gridFs)
	{
		// nothing to drop
	}

	@Override
	public void write(String collection, List<Document> docs)
	{
		List<Measure> measures = this.tracked.getOrDefault(collection, List.of());
		Map<Object, long[]> byId = this.documents.computeIfAbsent(collection, c -> new HashMap<>());
		for (Document doc : docs) {
			long[] values = new long[measures.size()];
			for (int i = 0; i < values.length; i++) {
				Measure m = measures.get(i);
				values[i] = switch (m.kind()) {
					case FIELD_EXISTS -> doc.containsKey(m.field()) ? 1 : 0;
					case ARRAY_SIZE -> doc.get(m.field()) instanceof List<?> l ? l.size() : 0;
					default -> 1;
				};
			}
			byId.put(doc.get("_id"), values);
		}
	}

	@Override
	public void uploadFile(String filename, Path file, Document metadata)
	{
		this.files++;
	}

	@Override
	public long measure(String collection, Measure measure)
	{
		if (measure.kind() == Measure.Kind.GRIDFS_FILES) {
			return this.files;
		}
		Map<Object, long[]> byId = this.documents.getOrDefault(collection, Map.of());
		if (measure.kind() == Measure.Kind.DOCUMENTS) {
			return byId.size();
		}
		int index = this.tracked.getOrDefault(collection, List.of()).indexOf(measure);
		if (index < 0) {
			throw new IllegalStateException("Measure not tracked: " + collection + " " + measure);
		}
		long total = 0;
		for (long[] values : byId.values()) {
			total += values[index];
		}
		return total;
	}

	@Override
	public long maxId(String collection)
	{
		long max = 0;
		for (Object id : this.documents.getOrDefault(collection, Map.of()).keySet()) {
			if (id instanceof Number n) {
				max = Math.max(max, n.longValue());
			}
		}
		return max;
	}

	@Override
	public void initSequence(String collection, long value, boolean exact)
	{
		this.sequences.merge(collection, value, exact ? (a, b) -> b : Math::max);
	}

	@Override
	public Long sequence(String collection)
	{
		return this.sequences.get(collection);
	}

	@Override
	public void close()
	{
		// nothing to close
	}
}
