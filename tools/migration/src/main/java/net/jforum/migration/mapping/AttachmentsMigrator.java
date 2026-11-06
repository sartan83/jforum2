package net.jforum.migration.mapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;

import net.jforum.migration.source.Columns;
import net.jforum.migration.target.BatchSink;
import net.jforum.migration.target.Measure;

/**
 * jforum_attach + jforum_attach_desc → <code>attachments</code> (_id = attach_id, the attach_desc columns
 * flattened in the same document, attach_desc_id kept as <code>attachDescId</code>).
 * With <code>--attachments-dir</code> the physical files (<code>&lt;dir&gt;/&lt;physical_filename&gt;</code> and the
 * optional <code>_thumb</code>) are copied into the GridFS bucket <code>fs</code> with
 * <code>metadata: {attachId, physicalFilename, realFilename, mimetype, thumb}</code>.
 */
public class AttachmentsMigrator implements TableMigrator
{
	private long filesUploaded;
	private final List<String> missingFiles = new ArrayList<>();

	@Override
	public String collection()
	{
		return "attachments";
	}

	@Override
	public List<String> tables()
	{
		return List.of("jforum_attach", "jforum_attach_desc");
	}

	@Override
	public List<ParitySpec> parity(MigrationContext ctx)
	{
		List<ParitySpec> specs = new ArrayList<>(List.of(
			ParitySpec.table("jforum_attach", "attachments", Measure.documents()),
			ParitySpec.table("jforum_attach_desc", "attachments", Measure.fieldExists("attachDescId"))));
		if (ctx.attachmentsDir() != null) {
			specs.add(new ParitySpec("files in " + ctx.attachmentsDir(), "fs.files", Measure.gridFsFiles(), this.filesUploaded));
		}
		return specs;
	}

	public List<String> missingFiles()
	{
		return this.missingFiles;
	}

	@Override
	public long migrate(MigrationContext ctx) throws Exception
	{
		EmbeddedChildren.warnIfMissing(ctx, "jforum_attach_desc", "attachment metadata");
		Map<Object, List<Document>> descriptions = EmbeddedChildren.load(ctx.source(), "jforum_attach_desc", "attach_id",
			"attach_id, attach_desc_id");
		Path dir = ctx.attachmentsDir();
		this.filesUploaded = 0;
		this.missingFiles.clear();
		long written;
		try (BatchSink sink = ctx.sink("attachments")) {
			ctx.source().stream("SELECT * FROM " + ctx.source().qualified("jforum_attach") + " ORDER BY attach_id", row -> {
				var doc = Columns.toDocument(row, "attach_id", Set.of());
				List<Document> desc = descriptions.getOrDefault(row.get("attach_id"), List.of());
				if (desc.size() > 1) {
					ctx.warn("jforum_attach_desc has " + desc.size() + " rows for attach_id " + row.get("attach_id")
						+ ": only attach_desc_id " + desc.get(0).get("attachDescId") + " is kept");
				}
				if (!desc.isEmpty()) {
					doc.putAll(desc.get(0));
					if (dir != null) {
						copyFiles(ctx, dir, doc);
					}
				}
				sink.add(doc);
			});
			sink.flush();
			written = sink.written();
		}
		if (!this.missingFiles.isEmpty()) {
			ctx.warn(this.missingFiles.size() + " attachment file(s) not found in " + dir + ": " + this.missingFiles);
		}
		return written;
	}

	private void copyFiles(MigrationContext ctx, Path dir, Document doc)
	{
		String physical = doc.getString("physicalFilename");
		if (physical == null || physical.isBlank()) {
			return;
		}
		Path file = dir.resolve(physical).normalize();
		if (!file.startsWith(dir.normalize()) || !Files.isRegularFile(file)) {
			this.missingFiles.add(physical);
			return;
		}
		String filename = doc.getString("realFilename") != null ? doc.getString("realFilename") : file.getFileName().toString();
		ctx.target().uploadFile(filename, file, metadata(doc, false));
		this.filesUploaded++;
		Path thumb = file.resolveSibling(file.getFileName() + "_thumb");
		if (Files.isRegularFile(thumb)) {
			ctx.target().uploadFile(filename + "_thumb", thumb, metadata(doc, true));
			this.filesUploaded++;
		}
	}

	private static Document metadata(Document doc, boolean thumb)
	{
		Document metadata = new Document("attachId", doc.get("_id"))
			.append("physicalFilename", doc.get("physicalFilename"))
			.append("realFilename", doc.get("realFilename"))
			.append("thumb", thumb);
		if (doc.get("mimetype") != null) {
			metadata.append("mimetype", doc.get("mimetype"));
		}
		return metadata;
	}
}
