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

import com.mongodb.client.gridfs.model.GridFSFile;

/**
 * Attachment contents (and their thumbnails) stored in the GridFS bucket <code>attachments</code>,
 * replacing the <code>attachments.store.dir</code> directory. Each file is named after the
 * legacy <code>physical_filename</code>, thumbnails get the legacy <code>_thumb</code> suffix.
 * The static accessor serves the legacy (non Spring managed) action classes.
 */
@Component
public class AttachmentFileStorage
{
	public static final String BUCKET = "attachments";
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

	public void store(String filename, InputStream content, String contentType, int attachId)
	{
		this.delete(filename);
		this.gridFs.store(content, filename, contentType, new Document("attachId", attachId));
	}

	public boolean exists(String filename)
	{
		return this.find(filename) != null;
	}

	/**
	 * @return the file, or <code>null</code> if there is no file with this name
	 */
	public GridFsResource open(String filename)
	{
		GridFSFile file = this.find(filename);
		return file == null ? null : this.gridFs.getResource(file);
	}

	public InputStream openStream(String filename) throws IOException
	{
		GridFsResource resource = this.open(filename);
		return resource == null ? null : resource.getInputStream();
	}

	public void delete(String filename)
	{
		this.gridFs.delete(byName(filename));
	}

	private GridFSFile find(String filename)
	{
		return filename == null ? null : this.gridFs.findOne(byName(filename));
	}

	private static Query byName(String filename)
	{
		return new Query(Criteria.where("filename").is(filename));
	}
}
