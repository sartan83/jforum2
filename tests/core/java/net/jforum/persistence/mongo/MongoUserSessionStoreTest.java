package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Date;

import net.jforum.boot.JForumApplication;
import net.jforum.document.SessionDocument;
import net.jforum.entities.UserSession;
import net.jforum.persistence.Stores;
import net.jforum.persistence.UserSessionStore;
import net.jforum.support.MongoTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoUserSessionStoreTest extends MongoTestSupport
{
	@Autowired
	private UserSessionStore sessions;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void clean()
	{
		this.mongo.remove(new Query(), SessionDocument.class);
	}

	private static UserSession session(int userId, String sessionId, Date start, long time)
	{
		UserSession us = new UserSession();
		us.setUserId(userId);
		us.setSessionId(sessionId);
		us.setStartTime(start);
		us.setSessionTime(time);
		us.setUsername("user" + userId);
		return us;
	}

	@Test
	void addSelectAndUpdate()
	{
		assertSame(this.sessions, Stores.usersession());

		Date start = new Date(1_000_000L);
		UserSession us = session(7, "abc", start, 500);
		assertNull(this.sessions.selectById(us));

		this.sessions.add(us);
		UserSession loaded = this.sessions.selectById(us);
		assertEquals(7, loaded.getUserId());
		assertEquals("user7", loaded.getUsername());
		assertEquals(start, loaded.getStartTime());
		// UserSessionModel.add does not write session_time
		assertEquals(0, loaded.getSessionTime());

		// A second add for the same user is a no-op
		this.sessions.add(session(7, "other", new Date(), 1));
		assertEquals(1, this.mongo.count(new Query(), SessionDocument.class));
		assertEquals("abc", this.mongo.findById(7, SessionDocument.class).getSessionId());

		Date later = new Date(2_000_000L);
		this.sessions.update(session(7, "def", later, 1234));
		UserSession updated = this.sessions.selectById(us);
		assertEquals(later, updated.getStartTime());
		assertEquals(1234, updated.getSessionTime());
		assertEquals("def", this.mongo.findById(7, SessionDocument.class).getSessionId());
	}

	@Test
	void updateInsertsWhenMissing()
	{
		this.sessions.update(session(9, "xyz", new Date(5000L), 10));

		SessionDocument d = this.mongo.findById(9, SessionDocument.class);
		assertEquals("xyz", d.getSessionId());
		assertEquals(new Date(5000L), d.getSessionStart());
	}
}
