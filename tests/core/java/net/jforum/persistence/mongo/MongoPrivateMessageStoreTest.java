package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import net.jforum.boot.JForumApplication;
import net.jforum.document.PrivateMessageDocument;
import net.jforum.entities.Post;
import net.jforum.entities.PrivateMessage;
import net.jforum.entities.PrivateMessageType;
import net.jforum.entities.User;
import net.jforum.persistence.PrivateMessageStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoPrivateMessageStoreTest extends MongoTestSupport
{
	@Autowired
	private PrivateMessageStore store;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void setUp()
	{
		this.mongo.dropCollection(PrivateMessageDocument.COLLECTION);
		this.mongo.remove(new org.springframework.data.mongodb.core.query.Query(), "users");
		this.mongo.insert(new Document("_id", 1).append("username", "alice"), "users");
		this.mongo.insert(new Document("_id", 2).append("username", "bob"), "users");
		this.mongo.insert(new Document("_id", 3).append("username", "carol"), "users");
	}

	private PrivateMessage send(int from, int to, String subject, long time)
	{
		PrivateMessage pm = new PrivateMessage();
		pm.setFromUser(user(from));
		pm.setToUser(user(to));
		Post p = new Post();
		p.setSubject(subject);
		p.setText("text of " + subject);
		p.setTime(new Date(time));
		p.setBbCodeEnabled(true);
		p.setHtmlEnabled(false);
		p.setSmiliesEnabled(true);
		p.setSignatureEnabled(false);
		pm.setPost(p);
		this.store.send(pm);
		return pm;
	}

	private static User user(int id)
	{
		User u = new User();
		u.setId(id);
		return u;
	}

	private static List<String> subjects(List<?> pms)
	{
		return pms.stream().map(o -> ((PrivateMessage)o).getPost().getSubject()).collect(Collectors.toList());
	}

	@Test
	void isRegisteredInStores()
	{
		assertSame(this.store, Stores.privatemessage());
	}

	@Test
	void sendStoresASentCopyAndANewInboxCopy()
	{
		PrivateMessage pm = this.send(1, 2, "hello", 1000);

		assertEquals(2, this.mongo.count(new org.springframework.data.mongodb.core.query.Query(), PrivateMessageDocument.class));

		List<?> inbox = this.store.selectFromInbox(user(2));
		assertEquals(1, inbox.size());
		PrivateMessage in = (PrivateMessage)inbox.get(0);
		assertEquals(pm.getId(), in.getId());
		assertEquals(PrivateMessageType.NEW, in.getType());
		assertEquals("hello", in.getPost().getSubject());
		assertEquals(1, in.getFromUser().getId());
		assertEquals("alice", in.getFromUser().getUsername());
		assertFalse(in.getFormatedDate().isEmpty());

		List<?> sent = this.store.selectFromSent(user(1));
		assertEquals(1, sent.size());
		PrivateMessage out = (PrivateMessage)sent.get(0);
		assertEquals(PrivateMessageType.SENT, out.getType());
		assertEquals(2, out.getToUser().getId());
		assertEquals("bob", out.getToUser().getUsername());
		assertTrue(out.getId() < pm.getId());

		assertTrue(this.store.selectFromInbox(user(1)).isEmpty());
		assertTrue(this.store.selectFromSent(user(2)).isEmpty());
	}

	@Test
	void listingsAreNewestFirstAndSkipMissingUsers()
	{
		this.send(1, 2, "first", 1000);
		this.send(3, 2, "third", 3000);
		this.send(1, 2, "second", 2000);
		this.send(99, 2, "ghost sender", 4000);
		this.send(1, 99, "ghost recipient", 5000);

		assertEquals(List.of("third", "second", "first"), subjects(this.store.selectFromInbox(user(2))));
		assertEquals(List.of("second", "first"), subjects(this.store.selectFromSent(user(1))));
	}

	@Test
	void selectByIdReturnsTheFullMessage()
	{
		PrivateMessage pm = this.send(1, 2, "full", 1000);

		PrivateMessage loaded = this.store.selectById(new PrivateMessage(pm.getId()));

		assertEquals(PrivateMessageType.NEW, loaded.getType());
		assertEquals("text of full", loaded.getPost().getText());
		assertEquals(new Date(1000), loaded.getPost().getTime());
		assertTrue(loaded.getPost().isBbCodeEnabled());
		assertFalse(loaded.getPost().isHtmlEnabled());
		assertTrue(loaded.getPost().isSmiliesEnabled());
		assertFalse(loaded.getPost().isSignatureEnabled());
		assertEquals("alice", loaded.getFromUser().getUsername());
		assertEquals("bob", loaded.getToUser().getUsername());

		PrivateMessage missing = new PrivateMessage(12345);
		assertSame(missing, this.store.selectById(missing));
	}

	@Test
	void updateTypeMarksReadAndSavedMessagesLeaveTheInbox()
	{
		PrivateMessage a = this.send(1, 2, "a", 1000);
		PrivateMessage b = this.send(1, 2, "b", 2000);

		a.setType(PrivateMessageType.READ);
		this.store.updateType(a);
		b.setType(PrivateMessageType.SAVED_IN);
		this.store.updateType(b);

		List<?> inbox = this.store.selectFromInbox(user(2));
		assertEquals(List.of("a"), subjects(inbox));
		assertEquals(PrivateMessageType.READ, ((PrivateMessage)inbox.get(0)).getType());
		assertEquals(PrivateMessageType.SAVED_IN, this.store.selectById(new PrivateMessage(b.getId())).getType());
	}

	@Test
	void deleteOnlyRemovesTheUsersOwnCopies()
	{
		PrivateMessage pm = this.send(1, 2, "to delete", 1000);
		int sentId = ((PrivateMessage)this.store.selectFromSent(user(1)).get(0)).getId();
		PrivateMessage[] both = { new PrivateMessage(pm.getId()), new PrivateMessage(sentId) };

		// a third user cannot delete anything
		this.store.delete(both, 3);
		assertEquals(1, this.store.selectFromInbox(user(2)).size());
		assertEquals(1, this.store.selectFromSent(user(1)).size());

		// the recipient removes only his inbox copy
		this.store.delete(both, 2);
		assertTrue(this.store.selectFromInbox(user(2)).isEmpty());
		assertEquals(1, this.store.selectFromSent(user(1)).size());

		// the sender removes his sent copy
		this.store.delete(both, 1);
		assertTrue(this.store.selectFromSent(user(1)).isEmpty());
	}

	@Test
	void savedMessagesCannotBeDeleted()
	{
		PrivateMessage pm = this.send(1, 2, "saved", 1000);
		pm.setType(PrivateMessageType.SAVED_IN);
		this.store.updateType(pm);

		this.store.delete(new PrivateMessage[] { new PrivateMessage(pm.getId()) }, 2);

		assertEquals(PrivateMessageType.SAVED_IN, this.store.selectById(new PrivateMessage(pm.getId())).getType());
	}
}
