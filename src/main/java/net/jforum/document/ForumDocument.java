package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_forums</code>. <code>forumTopics</code> and <code>forumLastPostId</code>
 * are the denormalized counters maintained by the forum/topic stores.
 */
@Document(collection = "forums")
public class ForumDocument
{
	@Id
	public int id;
	@Indexed
	public int categoriesId;
	public String forumName;
	public String forumDesc;
	@Indexed
	public int forumOrder;
	public int forumTopics;
	public int forumLastPostId;
	public int moderated;
}
