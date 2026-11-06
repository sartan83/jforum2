package net.jforum.persistence.mongo;

import static net.jforum.persistence.mongo.LegacyFixtures.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import net.jforum.boot.JForumApplication;
import net.jforum.entities.ForumModerationInfo;
import net.jforum.entities.ModerationPendingInfo;
import net.jforum.entities.Post;
import net.jforum.entities.TopicModerationInfo;
import net.jforum.persistence.ModerationStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoModerationStoreTest extends MongoTestSupport
{
	@Autowired
	private MongoTemplate mongo;

	private ModerationStore store;
	private LegacyFixtures f;

	@BeforeEach
	void setUp()
	{
		this.store = Stores.moderation();
		this.f = new LegacyFixtures(this.mongo);
		this.f.clearAll();

		this.f.category(1, "General");
		this.f.category(2, "Off topic");
		this.f.forum(10, 1, "Announcements");
		this.f.forum(11, 1, "Help");
		this.f.forum(20, 2, "Chat");
		this.f.user(1, "admin", "admin@example.com");
		this.f.user(2, "alice", "alice@example.com");

		this.f.topic(100, 10, "Pending topic", 0, 1000, at(1, 10));
		this.f.topic(101, 10, "Approved topic", 3, 1010, at(1, 9));
		this.f.topic(200, 11, "Help me", 0, 2000, at(2, 9));
		this.f.topic(300, 20, "Chatting", 1, 3000, at(3, 9));

		this.f.pendingPost(1000, 100, 10, 2, at(1, 10), "first", "pending first post");
		this.f.pendingPost(1002, 101, 10, 1, at(1, 13), "late", "pending later reply");
		this.f.pendingPost(1001, 101, 10, 2, at(1, 12), "early", "pending early reply");
		this.f.post(1010, 101, 10, 1, at(1, 9), "ok", "approved post");
		this.f.pendingPost(2000, 200, 11, 2, at(2, 9), "help", "pending help");
		this.f.pendingPost(2001, 200, 11, 99, at(2, 10), "ghost", "author does not exist");
		this.f.pendingPost(3001, 300, 20, 2, at(3, 10), "chat", "pending chat");
	}

	@Test
	void topicsByForumGroupsPendingPostsByTopicInTimeOrder()
	{
		Map<Integer, TopicModerationInfo> topics = this.store.topicsByForum(10);

		assertEquals(List.of(100, 101), List.copyOf(topics.keySet()));

		TopicModerationInfo pending = topics.get(100);
		assertEquals("Pending topic", pending.getTopicTitle());
		assertEquals(0, pending.getTopicReplies());
		assertEquals(1, pending.getPosts().size());

		TopicModerationInfo approved = topics.get(101);
		assertEquals(3, approved.getTopicReplies());
		List<Post> posts = approved.getPosts();
		assertEquals(List.of(1001, 1002), posts.stream().map(Post::getId).toList());

		Post p = posts.get(0);
		assertEquals("alice", p.getPostUsername());
		assertEquals(2, p.getUserId());
		assertEquals("early", p.getSubject());
		assertEquals("pending early reply", p.getText());
		assertTrue(p.isBbCodeEnabled());
		assertFalse(p.isHtmlEnabled());
		assertTrue(p.isSmiliesEnabled());

		Map<Integer, TopicModerationInfo> help = this.store.topicsByForum(11);
		assertEquals(1, help.get(200).getPosts().size(), "posts of unknown users are left out (inner join)");
		assertTrue(this.store.topicsByForum(99).isEmpty());
	}

	@Test
	void categoryPendingModerationCountsPendingPostsPerForum()
	{
		List<ModerationPendingInfo> infos = this.store.categoryPendingModeration();

		assertEquals(2, infos.size());
		assertEquals(1, infos.get(0).getCategoryId());
		assertEquals("General", infos.get(0).getCategoryName());

		List<ForumModerationInfo> general = infos.get(0).getForums();
		assertEquals(2, general.size());
		assertEquals(10, general.get(0).getForumId());
		assertEquals("Announcements", general.get(0).getForumName());
		assertEquals(3, general.get(0).getPostsToModerate());
		assertEquals(11, general.get(1).getForumId());
		assertEquals(2, general.get(1).getPostsToModerate());

		assertEquals("Off topic", infos.get(1).getCategoryName());
		assertEquals(1, ((ForumModerationInfo)infos.get(1).getForums().get(0)).getPostsToModerate());
	}

	@Test
	void aprovePostClearsTheFlagAndRefreshesThePostTime()
	{
		Date before = new Date(System.currentTimeMillis() - 1000);

		this.store.aprovePost(1001);

		Document post = this.mongo.findById(1001, Document.class, "posts");
		assertEquals(0, post.get("needModerate"));
		assertTrue(post.getDate("postTime").after(before));

		assertEquals(List.of(1002), ((List<Post>)((TopicModerationInfo)this.store.topicsByForum(10).get(101)).getPosts())
			.stream().map(Post::getId).toList());

		ModerationPendingInfo general = (ModerationPendingInfo)this.store.categoryPendingModeration().get(0);
		assertEquals(2, ((ForumModerationInfo)general.getForums().get(0)).getPostsToModerate());

		// boolean flags written by other writers keep their type
		this.mongo.save(this.mongo.findById(3001, Document.class, "posts").append("needModerate", true), "posts");
		assertEquals(1, this.store.topicsByForum(20).size());
		this.store.aprovePost(3001);
		assertEquals(false, this.mongo.findById(3001, Document.class, "posts").get("needModerate"));
		assertTrue(this.store.topicsByForum(20).isEmpty());

		this.store.aprovePost(424242);
	}
}
