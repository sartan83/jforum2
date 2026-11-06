package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** <code>jforum_quota_limit</code>. */
@Document(collection = "quota_limit")
public class QuotaLimitDocument
{
	@Id
	public int id;

	public String quotaDesc;
	public int quotaLimit;
	public int quotaType = 1;
}
