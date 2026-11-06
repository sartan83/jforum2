package net.jforum.persistence.mongo;

import java.util.Date;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Raw documents of the collections owned by the other areas (posts, topics, users, forums,
 * categories), shaped as the shared conventions define them.
 */
public final class LegacyFixtures
{
	private final MongoTemplate mongo;

	public LegacyFixtures(MongoTemplate mongo)
	{
		this.mongo = mongo;
	}

	/** Removes the documents but keeps the indexes (the text index on posts). */
	public void clear(String... collections)
	{
		for (String c : collections) {
			this.mongo.remove(new Query(), c);
		}
	}

	public void clearAll()
	{
		this.clear("posts", "topics", "users", "forums", "categories");
	}

	public void category(int id, String title)
	{
		this.mongo.insert(new Document("_id", id).append("title", title), "categories");
	}

	public void forum(int id, int categoryId, String name)
	{
		this.mongo.insert(new Document("_id", id).append("categoriesId", categoryId).append("forumName", name), "forums");
	}

	public void user(int id, String username, String email)
	{
		this.mongo.insert(new Document("_id", id).append("username", username).append("userEmail", email), "users");
	}

	public void topic(int id, int forumId, String title, int replies, int firstPostId, Date time)
	{
		this.mongo.insert(new Document("_id", id)
			.append("forumId", forumId)
			.append("topicTitle", title)
			.append("topicReplies", replies)
			.append("topicFirstPostId", firstPostId)
			.append("topicTime", time), "topics");
	}

	public Document post(int id, int topicId, int forumId, int userId, Date time, String subject, String text)
	{
		Document d = new Document("_id", id)
			.append("topicId", topicId)
			.append("forumId", forumId)
			.append("userId", userId)
			.append("postTime", time)
			.append("subject", subject)
			.append("text", text)
			.append("enableBbcode", 1)
			.append("enableHtml", 0)
			.append("enableSmilies", 1)
			.append("status", 1)
			.append("attach", 0)
			.append("needModerate", 0);
		this.mongo.insert(d, "posts");
		return d;
	}

	public void pendingPost(int id, int topicId, int forumId, int userId, Date time, String subject, String text)
	{
		Document d = this.post(id, topicId, forumId, userId, time, subject, text);
		this.mongo.save(d.append("needModerate", 1), "posts");
	}

	public static Date at(int day, int hour)
	{
		return new Date(Date.UTC(126, 0, day, hour, 0, 0));
	}
}
