package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.jforum.boot.JForumApplication;
import net.jforum.document.ModerationLogDocument;
import net.jforum.entities.ModerationLog;
import net.jforum.entities.User;
import net.jforum.persistence.ModerationLogStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoModerationLogStoreTest extends MongoTestSupport
{
	@Autowired
	private MongoTemplate mongo;

	private ModerationLogStore store;

	@BeforeEach
	void setUp()
	{
		this.store = Stores.moderationlog();
		this.mongo.remove(new Query(), ModerationLogDocument.class);

		LegacyFixtures f = new LegacyFixtures(this.mongo);
		f.clear("users");
		f.user(1, "admin", "admin@example.com");
		f.user(2, "alice", "alice@example.com");
	}

	@Test
	void addsAndPagesNewestFirstWithUsernames()
	{
		for (int i = 1; i <= 5; i++) {
			ModerationLog log = log(1, i % 2 == 0 ? 2 : 99, "action " + i);
			this.store.add(log);
			assertTrue(log.getId() > 0);
		}

		assertEquals(5, this.store.totalRecords());

		List<ModerationLog> firstPage = this.store.selectAll(0, 2);
		assertEquals(List.of("action 5", "action 4"), firstPage.stream().map(ModerationLog::getDescription).toList());

		List<ModerationLog> lastPage = this.store.selectAll(4, 2);
		assertEquals(List.of("action 1"), lastPage.stream().map(ModerationLog::getDescription).toList());

		ModerationLog l = firstPage.get(1);
		assertEquals("admin", l.getUser().getUsername());
		assertEquals(1, l.getUser().getId());
		assertEquals("alice", l.getPosterUser().getUsername());
		assertEquals("original action 4", l.getOriginalMessage());
		assertEquals(3, l.getType());
		assertEquals(40, l.getPostId());
		assertEquals(400, l.getTopicId());
		assertNotNull(l.getDate());

		ModerationLog unknownPoster = firstPage.get(0);
		assertEquals(99, unknownPoster.getPosterUser().getId());
		assertNull(unknownPoster.getPosterUser().getUsername(), "left join: unknown users have no name");

		assertTrue(this.store.selectAll(10, 2).isEmpty());
	}

	private static ModerationLog log(int userId, int posterId, String description)
	{
		int n = Integer.parseInt(description.substring(description.length() - 1));

		User user = new User();
		user.setId(userId);
		User poster = new User();
		poster.setId(posterId);

		ModerationLog log = new ModerationLog();
		log.setUser(user);
		log.setPosterUser(poster);
		log.setDescription(description);
		log.setOriginalMessage("original " + description);
		log.setType(3);
		log.setPostId(n * 10);
		log.setTopicId(n * 100);
		return log;
	}
}
