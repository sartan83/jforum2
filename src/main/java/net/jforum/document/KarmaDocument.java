package net.jforum.document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_karma</code>: one vote of <code>fromUserId</code> on a post written by <code>postUserId</code>.
 */
@Document(collection = KarmaDocument.COLLECTION)
@CompoundIndexes({
	@CompoundIndex(name = "post_from", def = "{'postId': 1, 'fromUserId': 1}"),
	@CompoundIndex(name = "topic_from", def = "{'topicId': 1, 'fromUserId': 1}"),
	@CompoundIndex(name = "postuser_date", def = "{'postUserId': 1, 'rateDate': 1}")
})
public class KarmaDocument
{
	public static final String COLLECTION = "karma";

	@Id
	private int id;
	@Indexed
	private int postId;
	private int topicId;
	private int postUserId;
	@Indexed
	private int fromUserId;
	private int points;
	@Indexed
	private Date rateDate;

	public int getId() { return this.id; }
	public void setId(int id) { this.id = id; }
	public int getPostId() { return this.postId; }
	public void setPostId(int postId) { this.postId = postId; }
	public int getTopicId() { return this.topicId; }
	public void setTopicId(int topicId) { this.topicId = topicId; }
	public int getPostUserId() { return this.postUserId; }
	public void setPostUserId(int postUserId) { this.postUserId = postUserId; }
	public int getFromUserId() { return this.fromUserId; }
	public void setFromUserId(int fromUserId) { this.fromUserId = fromUserId; }
	public int getPoints() { return this.points; }
	public void setPoints(int points) { this.points = points; }
	public Date getRateDate() { return this.rateDate; }
	public void setRateDate(Date rateDate) { this.rateDate = rateDate; }
}
