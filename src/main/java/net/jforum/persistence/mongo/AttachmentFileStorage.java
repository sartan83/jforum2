package net.jforum.persistence.mongo;

import java.io.IOException;
import java.io.InputStream;

import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

/**
 * Attachment contents (and their thumbnails) stored in GridFS, replacing the
 * <code>attachments.store.dir</code> directory. Same layout as the migration tool
 * (<code>tools/migration</code>): default bucket <code>fs</code>, file name = real file name,
 * <code>metadata: {attachId, physicalFilename, realFilename, mimetype, thumb}</code>.
 * Files are looked up by <code>metadata.physicalFilename</code> + <code>metadata.thumb</code>;
 * callers address a thumbnail with the legacy name <code>physicalFilename + "_thumb"</code>.
 * The static accessor serves the legacy (non Spring managed) action classes.
 */
@Component
public class AttachmentFileStorage
{
	public static final String BUCKET = "fs";
	public static final String THUMB_SUFFIX = "_thumb";

	private static AttachmentFileStorage instance;

	private final GridFsTemplate gridFs;

	public AttachmentFileStorage(MongoDatabaseFactory factory, MongoConverter converter)
	{
		this.gridFs = new GridFsTemplate(factory, converter, BUCKET);
		instance = this;
	}

	public static AttachmentFileStorage get()
	{
		if (instance == null) {
			throw new IllegalStateException("Attachment storage not initialized");
		}

		return instance;
	}

	/**
	 * @param name the physical file name, or the physical file name + <code>_thumb</code> for a thumbnail
	 */
	public void store(String name, InputStream content, String contentType, int attachId, String realFilename)
	{
		this.delete(name);

		boolean thumb = isThumb(name);
		String physical = physical(name);
		String filename = realFilename != null ? realFilename : physical;

		Document metadata = new Document("attachId", attachId)
			.append("physicalFilename", physical)
			.append("realFilename", realFilename)
			.append("mimetype", contentType)
			.append("thumb", thumb);

		this.gridFs.store(content, thumb ? filename + THUMB_SUFFIX : filename, contentType, metadata);
	}

	public boolean exists(String name)
	{
		return name != null && this.gridFs.findOne(byName(name)) != null;
	}

	/**
	 * @return the file, or <code>null</code> if there is no such file
	 */
	public GridFsResource open(String name)
	{
		if (name == null) {
			return null;
		}

		var file = this.gridFs.findOne(byName(name));
		return file == null ? null : this.gridFs.getResource(file);
	}

	public InputStream openStream(String name) throws IOException
	{
		GridFsResource resource = this.open(name);
		return resource == null ? null : resource.getInputStream();
	}

	public void delete(String name)
	{
		if (name != null) {
			this.gridFs.delete(byName(name));
		}
	}

	private static boolean isThumb(String name)
	{
		return name.endsWith(THUMB_SUFFIX);
	}

	private static String physical(String name)
	{
		return isThumb(name) ? name.substring(0, name.length() - THUMB_SUFFIX.length()) : name;
	}

	private static Query byName(String name)
	{
		return new Query(new Criteria().andOperator(
			Criteria.where("metadata.physicalFilename").is(physical(name)),
			isThumb(name)
				? Criteria.where("metadata.thumb").is(true)
				: Criteria.where("metadata.thumb").ne(true)));
	}
}
