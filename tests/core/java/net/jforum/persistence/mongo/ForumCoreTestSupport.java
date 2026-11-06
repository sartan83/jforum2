package net.jforum.persistence.mongo;

import java.util.Date;
import java.util.List;

import net.jforum.boot.JForumApplication;
import net.jforum.entities.Category;
import net.jforum.entities.Forum;
import net.jforum.entities.Post;
import net.jforum.entities.Topic;
import net.jforum.entities.User;
import net.jforum.support.MongoTestSupport;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Shared fixture for the forum core stores: clean collections and helpers reproducing the
 * calls made by the posting actions (topic + first post, replies, counters).
 */
@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class ForumCoreTestSupport extends MongoTestSupport
{
	private static final List<String> COLLECTIONS = List.of("categories", "forums", "forums_watch", "topics",
		"topics_watch", "posts", "polls", "config", "users", "roles", "groups", "mail_integration");

	@Autowired
	protected MongoTemplate mongo;

	@Autowired
	protected MongoCategoryStore categories;

	@Autowired
	protected MongoForumStore forums;

	@Autowired
	protected MongoTopicStore topics;

	@Autowired
	protected MongoPostStore posts;

	@Autowired
	protected MongoPollStore polls;

	@Autowired
	protected MongoConfigStore configs;

	@Autowired
	protected ForumAccessResolver forumAccess;

	@BeforeEach
	void cleanCollections()
	{
		COLLECTIONS.forEach(c -> this.mongo.remove(new Query(), c));
		this.forumAccess.setAllowedForums(() -> "");
	}

	@AfterEach
	void restoreForumAccess()
	{
		this.forumAccess.setAllowedForums(null);
	}

	protected void allowForums(Integer... forumIds)
	{
		String ids = java.util.Arrays.stream(forumIds).map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
		this.forumAccess.setAllowedForums(() -> ids);
	}

	protected void user(int id, String username)
	{
		this.mongo.insert(new Document("_id", id).append("username", username)
			.append("userEmail", username + "@example.com")
			.append("userLang", "en_US")
			.append("userNotifyAlways", 0)
			.append("userNotifyText", 1)
			.append("userRegdate", new Date(System.currentTimeMillis() - 10L * 24 * 3600 * 1000)), "users");
	}

	protected int category(String name)
	{
		Category c = new Category();
		c.setName(name);
		return this.categories.addNew(c);
	}

	protected int forum(int categoryId, String name)
	{
		Forum f = new Forum();
		f.setIdCategories(categoryId);
		f.setName(name);
		f.setDescription(name + " description");
		return this.forums.addNew(f);
	}

	/** New topic with its first post, as done by <code>PostAction.insertSave</code>. */
	protected Topic topic(int forumId, int userId, String title, long time)
	{
		Topic t = new Topic();
		t.setForumId(forumId);
		t.setTitle(title);
		t.setTime(new Date(time));
		User u = new User();
		u.setId(userId);
		t.setPostedBy(u);
		this.topics.addNew(t);

		int postId = this.posts.addNew(post(t.getId(), forumId, userId, title, time));
		t.setFirstPostId(postId);
		t.setLastPostId(postId);
		this.topics.update(t);
		this.forums.incrementTotalTopics(forumId, 1);
		this.forums.setLastPost(forumId, postId);
		return t;
	}

	/** Reply to a topic, as done by <code>PostAction.insertSave</code>. */
	protected int reply(Topic t, int userId, String text, long time)
	{
		int postId = this.posts.addNew(post(t.getId(), t.getForumId(), userId, text, time));
		this.topics.setLastPostId(t.getId(), postId);
		this.topics.incrementTotalReplies(t.getId());
		this.forums.setLastPost(t.getForumId(), postId);
		return postId;
	}

	protected static Post post(int topicId, int forumId, int userId, String text, long time)
	{
		Post p = new Post();
		p.setTopicId(topicId);
		p.setForumId(forumId);
		p.setUserId(userId);
		p.setTime(new Date(time));
		p.setSubject("Re: " + text);
		p.setText(text);
		p.setUserIp("10.0.0." + userId);
		p.setBbCodeEnabled(true);
		p.setSmiliesEnabled(true);
		p.setSignatureEnabled(true);
		return p;
	}
}
