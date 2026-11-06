package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_forums_watch</code> (no legacy primary key: <code>_id</code> is generated).
 */
@Document(collection = "forums_watch")
@CompoundIndex(name = "forum_user", def = "{'forumId': 1, 'userId': 1}")
public class ForumWatchDocument
{
	@Id
	public String id;
	public int forumId;
	@Indexed
	public int userId;
}
