package net.jforum.persistence.mongo;

import static net.jforum.persistence.mongo.LegacyFixtures.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.jforum.boot.JForumApplication;
import net.jforum.entities.Post;
import net.jforum.persistence.Stores;
import net.jforum.persistence.SummaryStore;
import net.jforum.support.MongoTestSupport;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoSummaryStoreTest extends MongoTestSupport
{
	@Autowired
	private MongoTemplate mongo;

	private SummaryStore store;
	private LegacyFixtures f;

	@BeforeEach
	void setUp()
	{
		this.store = Stores.summary();
		this.f = new LegacyFixtures(this.mongo);
		this.f.clearAll();

		this.f.user(1, "admin", "admin@example.com");
		this.f.user(2, "alice", "alice@example.com");
		this.mongo.insert(new Document("_id", 3).append("username", "noemail").append("userEmail", " "), "users");
		this.mongo.insert(new Document("_id", 4).append("username", "legacy").append("email", "legacy@example.com"), "users");
	}

	@Test
	void selectsTheFirstPostOfTheTopicsCreatedInThePeriodNewestFirst()
	{
		// in the period
		this.f.topic(1, 1, "Old in period", 2, 10, at(2, 10));
		this.f.post(10, 1, 1, 1, at(2, 10), "first of 1", "text 1");
		this.f.post(11, 1, 1, 2, at(8, 10), "reply of 1", "reply");
		this.f.topic(2, 1, "New in period", 0, 20, at(6, 10));
		this.f.post(20, 2, 1, 2, at(6, 10), "first of 2", "text 2");
		// pending first post
		this.f.topic(3, 1, "Pending", 0, 30, at(5, 10));
		this.f.pendingPost(30, 3, 1, 2, at(5, 10), "pending", "pending");
		// inactive (status = 0) first post
		this.f.topic(4, 1, "Inactive", 0, 40, at(5, 11));
		this.mongo.save(this.f.post(40, 4, 1, 2, at(5, 11), "inactive", "x").append("status", 0), "posts");
		// out of the period
		this.f.topic(5, 1, "Too old", 0, 50, at(1, 9));
		this.f.post(50, 5, 1, 2, at(1, 9), "too old", "x");

		List<Post> posts = this.store.selectLastPosts(at(1, 12), at(7, 12));

		assertEquals(List.of(20, 10), posts.stream().map(Post::getId).toList());

		Post p = posts.get(0);
		assertEquals(2, p.getTopicId());
		assertEquals(1, p.getForumId());
		assertEquals(2, p.getUserId());
		assertEquals("alice", p.getPostUsername());
		assertEquals("first of 2", p.getSubject());
		assertEquals("text 2", p.getText());
		assertEquals(at(6, 10), p.getTime());
		assertNotNull(p.getFormatedTime());

		assertTrue(this.store.selectLastPosts(at(20, 0), at(21, 0)).isEmpty());
	}

	@Test
	void recipientsAreAllUsersWithAnEmail()
	{
		assertEquals(List.of("admin@example.com", "alice@example.com", "legacy@example.com"), this.store.listRecipients());
	}
}
