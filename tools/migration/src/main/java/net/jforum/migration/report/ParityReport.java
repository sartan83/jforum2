package net.jforum.migration.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a migration run, written as <code>parity-report.json</code> and <code>parity-report.txt</code>.
 */
public class ParityReport
{
	public String source;
	public String target;
	public boolean dryRun;
	public Instant startedAt;
	public Instant finishedAt;
	public List<ParityEntry> tables = new ArrayList<>();
	public List<SequenceEntry> sequences = new ArrayList<>();
	public List<String> missingTables = new ArrayList<>();
	public List<String> warnings = new ArrayList<>();

	@JsonProperty("status")
	public String status()
	{
		return isOk() ? "OK" : "MISMATCH";
	}

	public boolean isOk()
	{
		return this.tables.stream().allMatch(ParityEntry::match) && this.sequences.stream().allMatch(SequenceEntry::ok);
	}

	public ParityEntry entry(String source, String measure)
	{
		return this.tables.stream().filter(e -> e.source().equals(source) && e.measure().equals(measure)).findFirst().orElse(null);
	}

	private static int width(List<String> values, String header)
	{
		return values.stream().mapToInt(String::length).max().orElse(0) > header.length()
			? values.stream().mapToInt(String::length).max().getAsInt() : header.length();
	}

	public String toText()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("JForum SQL Server -> MongoDB parity report").append(this.dryRun ? " (DRY RUN, nothing written)" : "").append('\n');
		sb.append("source:   ").append(this.source).append('\n');
		sb.append("target:   ").append(this.target).append('\n');
		sb.append("started:  ").append(this.startedAt).append('\n');
		sb.append("finished: ").append(this.finishedAt).append("\n\n");
		int src = width(this.tables.stream().map(ParityEntry::source).toList(), "SOURCE");
		int col = width(this.tables.stream().map(ParityEntry::target).toList(), "COLLECTION");
		int mea = width(this.tables.stream().map(ParityEntry::measure).toList(), "MEASURE");
		String format = "%-6s %-" + src + "s %10s  %-" + col + "s %-" + mea + "s %10s%n";
		sb.append(String.format(format, "", "SOURCE", "ROWS", "COLLECTION", "MEASURE", "TARGET"));
		for (ParityEntry e : this.tables) {
			sb.append(String.format(format, e.match() ? "OK" : "DIFF", e.source(), e.sourceRows(), e.target(), e.measure(), e.targetCount()));
		}
		int seq = width(this.sequences.stream().map(SequenceEntry::collection).toList(), "SEQUENCE");
		String seqFormat = "%-6s %-" + seq + "s %10s %10s%n";
		sb.append('\n').append(String.format(seqFormat, "", "SEQUENCE", "MAX(_id)", "VALUE"));
		for (SequenceEntry s : this.sequences) {
			sb.append(String.format(seqFormat, s.ok() ? "OK" : "DIFF", s.collection(), s.maxId(), s.value()));
		}
		if (!this.missingTables.isEmpty()) {
			sb.append("\nTables not found in the source (skipped): ").append(String.join(", ", this.missingTables)).append('\n');
		}
		for (String w : this.warnings) {
			sb.append("WARNING: ").append(w).append('\n');
		}
		sb.append("\nSTATUS: ").append(status()).append('\n');
		return sb.toString();
	}
}
