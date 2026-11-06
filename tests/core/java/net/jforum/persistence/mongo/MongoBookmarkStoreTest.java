package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import net.jforum.boot.JForumApplication;
import net.jforum.document.BookmarkDocument;
import net.jforum.entities.Bookmark;
import net.jforum.entities.BookmarkType;
import net.jforum.exceptions.InvalidBookmarkTypeException;
import net.jforum.persistence.BookmarkStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoBookmarkStoreTest extends MongoTestSupport
{
	private static final int OWNER = 5;

	@Autowired
	private BookmarkStore store;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void setUp()
	{
		this.mongo.dropCollection(BookmarkDocument.COLLECTION);
		for (String c : List.of("forums", "topics", "users")) {
			this.mongo.remove(new org.springframework.data.mongodb.core.query.Query(), c);
		}
		this.mongo.insert(new Document("_id", 1).append("forumName", "Zeta forum").append("forumDesc", "zeta desc"), "forums");
		this.mongo.insert(new Document("_id", 2).append("forumName", "Alpha forum").append("forumDesc", "alpha desc"), "forums");
		this.mongo.insert(new Document("_id", 10).append("topicTitle", "Some topic"), "topics");
		this.mongo.insert(new Document("_id", 20).append("username", "friend"), "users");
	}

	private Bookmark add(int type, int relationId, String title, String description, boolean publicVisible)
	{
		Bookmark b = new Bookmark();
		b.setUserId(OWNER);
		b.setRelationType(type);
		b.setRelationId(relationId);
		b.setTitle(title);
		b.setDescription(description);
		b.setPublicVisible(publicVisible);
		this.store.add(b);
		return b;
	}

	private static List<String> titles(List<?> l)
	{
		return l.stream().map(o -> ((Bookmark)o).getTitle()).collect(Collectors.toList());
	}

	@Test
	void crud()
	{
		assertSame(this.store, Stores.bookmark());

		Bookmark b = this.add(BookmarkType.TOPIC, 10, "My topic", "notes", true);
		assertTrue(b.getId() > 0);

		Bookmark loaded = this.store.selectById(b.getId());
		assertEquals(OWNER, loaded.getUserId());
		assertEquals(10, loaded.getRelationId());
		assertEquals(BookmarkType.TOPIC, loaded.getRelationType());
		assertEquals("My topic", loaded.getTitle());
		assertEquals("notes", loaded.getDescription());
		assertTrue(loaded.isPublicVisible());

		Bookmark forUpdate = this.store.selectForUpdate(10, BookmarkType.TOPIC, OWNER);
		assertEquals(b.getId(), forUpdate.getId());
		assertNull(this.store.selectForUpdate(10, BookmarkType.FORUM, OWNER));
		assertNull(this.store.selectForUpdate(10, BookmarkType.TOPIC, OWNER + 1));

		forUpdate.setTitle("Renamed");
		forUpdate.setDescription("new notes");
		forUpdate.setPublicVisible(false);
		forUpdate.setRelationId(999); // not updatable
		this.store.update(forUpdate);
		Bookmark updated = this.store.selectById(b.getId());
		assertEquals("Renamed", updated.getTitle());
		assertEquals("new notes", updated.getDescription());
		assertFalse(updated.isPublicVisible());
		assertEquals(10, updated.getRelationId());

		this.store.remove(b.getId());
		assertNull(this.store.selectById(b.getId()));
	}

	@Test
	void selectByTypeJoinsTheRelatedDocument()
	{
		this.add(BookmarkType.FORUM, 1, "", null, true);
		this.add(BookmarkType.FORUM, 2, "Custom", "custom desc", true);
		this.add(BookmarkType.FORUM, 3, "Deleted forum", null, true);
		this.add(BookmarkType.TOPIC, 10, null, "t", true);
		this.add(BookmarkType.USER, 20, "", "u", false);

		List<?> forums = this.store.selectByUser(OWNER, BookmarkType.FORUM);
		// ordered by forum name; bookmark of the missing forum is dropped (inner join)
		assertEquals(List.of("Custom", "Zeta forum"), titles(forums));
		assertEquals("custom desc", ((Bookmark)forums.get(0)).getDescription());
		assertEquals("zeta desc", ((Bookmark)forums.get(1)).getDescription());

		assertEquals(List.of("Some topic"), titles(this.store.selectByUser(OWNER, BookmarkType.TOPIC)));
		List<?> users = this.store.selectByUser(OWNER, BookmarkType.USER);
		assertEquals(List.of("friend"), titles(users));
		assertFalse(((Bookmark)users.get(0)).isPublicVisible());

		assertTrue(this.store.selectByUser(OWNER + 1, BookmarkType.FORUM).isEmpty());
		assertThrows(InvalidBookmarkTypeException.class, () -> this.store.selectByUser(OWNER, 42));
	}

	@Test
	void selectAllOfAUserIsOrderedByTitle()
	{
		this.add(BookmarkType.FORUM, 1, "b", null, true);
		this.add(BookmarkType.TOPIC, 10, "c", null, true);
		this.add(BookmarkType.USER, 20, "a", null, true);
		Bookmark other = new Bookmark();
		other.setUserId(OWNER + 1);
		other.setRelationType(BookmarkType.FORUM);
		other.setRelationId(2);
		other.setTitle("other");
		this.store.add(other);

		assertEquals(List.of("a", "b", "c"), titles(this.store.selectByUser(OWNER)));
	}
}
