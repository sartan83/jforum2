package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_posts</code> merged with <code>jforum_posts_text</code>
 * (<code>subject</code>, <code>text</code>, as written by the migration tool).
 */
@Document(collection = "posts")
@CompoundIndexes({
	@CompoundIndex(name = "topic_time", def = "{'topicId': 1, 'postTime': 1}"),
	@CompoundIndex(name = "user_post", def = "{'userId': 1, '_id': -1}")
})
public class PostDocument
{
	@Id
	public int id;
	@Indexed
	public int topicId;
	@Indexed
	public int forumId;
	@Indexed
	public int userId;
	@Indexed
	public Date postTime;
	public String posterIp;
	public int enableBbcode = 1;
	public int enableHtml = 1;
	public int enableSmilies = 1;
	public int enableSig = 1;
	public Date postEditTime;
	public int postEditCount;
	public int status = 1;
	public int attach;
	@Indexed
	public int needModerate;
	public String subject;
	public String text;
}
