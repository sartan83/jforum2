package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_topics_watch</code> (no legacy primary key: <code>_id</code> is generated).
 */
@Document(collection = "topics_watch")
@CompoundIndex(name = "topic_user", def = "{'topicId': 1, 'userId': 1}")
public class TopicWatchDocument
{
	@Id
	public String id;
	public int topicId;
	@Indexed
	public int userId;
	public int isRead = 1;
}
