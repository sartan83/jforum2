package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_topics</code>. The compound indexes serve the forum listing
 * (<code>forumId OR topicMovedId</code>, ordered by <code>topicType DESC, topicLastPostId DESC</code>).
 */
@Document(collection = "topics")
@CompoundIndexes({
	@CompoundIndex(name = "forum_listing", def = "{'forumId': 1, 'topicType': -1, 'topicLastPostId': -1}"),
	@CompoundIndex(name = "moved_listing", def = "{'topicMovedId': 1, 'topicType': -1, 'topicLastPostId': -1}"),
	@CompoundIndex(name = "user_listing", def = "{'userId': 1, 'topicLastPostId': -1}")
})
public class TopicDocument
{
	@Id
	public int id;
	@Indexed
	public int forumId;
	public String topicTitle;
	public int userId;
	public Date topicTime;
	@Indexed
	public int topicViews;
	public int topicReplies;
	public int topicStatus;
	public int topicVoteId;
	public int topicType;
	@Indexed
	public int topicFirstPostId;
	@Indexed
	public int topicLastPostId;
	public int topicMovedId;
	public boolean moderated;
}
