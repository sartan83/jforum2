package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * <code>jforum_attach</code> merged with its 1:1 <code>jforum_attach_desc</code> row.
 * The file contents live in the GridFS bucket <code>attachments</code>, under
 * <code>physicalFilename</code> (and <code>physicalFilename + "_thumb"</code> for thumbnails).
 */
@Document(collection = "attach")
public class AttachmentDocument
{
	@Id
	public int id;

	@Indexed
	public int postId;

	@Indexed
	public int privmsgsId;

	@Indexed
	public int userId;

	public String physicalFilename;
	public String realFilename;
	public int downloadCount;
	public String description;
	public String mimetype;
	public long filesize;
	public Date uploadTime;
	public int thumb;

	@Indexed
	public int extensionId;
}
