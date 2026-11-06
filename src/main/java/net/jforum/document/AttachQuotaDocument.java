package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** <code>jforum_attach_quota</code>: quota limit assigned to a group. */
@Document(collection = "attach_quota")
public class AttachQuotaDocument
{
	@Id
	public int id;

	@Indexed
	public int groupId;

	public int quotaLimitId;
}
