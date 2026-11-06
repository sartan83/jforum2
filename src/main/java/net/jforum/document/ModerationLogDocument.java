package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** <code>jforum_moderation_log</code>. */
@Document(collection = "moderation_log")
public class ModerationLogDocument
{
	@Id
	public int id;

	@Indexed
	public int userId;

	public String logDescription;
	public String logOriginalMessage;
	public Date logDate;
	public int logType;
	public int postId;
	public int topicId;

	@Indexed
	public int postUserId;
}
