package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_posts</code> merged with <code>jforum_posts_text</code>
 * (<code>postSubject</code>, <code>postText</code>).
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
	public boolean enableBbcode = true;
	public boolean enableHtml = true;
	public boolean enableSmilies = true;
	public boolean enableSig = true;
	public Date postEditTime;
	public int postEditCount;
	public int status = 1;
	public boolean attach;
	@Indexed
	public boolean needModerate;
	public String postSubject;
	public String postText;
}
