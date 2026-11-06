package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import net.jforum.boot.JForumApplication;
import net.jforum.document.KarmaDocument;
import net.jforum.entities.Karma;
import net.jforum.entities.User;
import net.jforum.exceptions.DatabaseException;
import net.jforum.persistence.KarmaStore;
import net.jforum.persistence.Stores;
import net.jforum.support.MongoTestSupport;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoKarmaStoreTest extends MongoTestSupport
{
	private static final int AUTHOR = 10;
	private static final int OTHER_AUTHOR = 13;
	private static final int VOTER1 = 11;
	private static final int VOTER2 = 12;

	@Autowired
	private KarmaStore store;

	@Autowired
	private MongoTemplate mongo;

	@BeforeEach
	void setUp()
	{
		this.mongo.dropCollection(KarmaDocument.COLLECTION);
		this.mongo.remove(new org.springframework.data.mongodb.core.query.Query(), "users");
		this.mongo.insert(new Document("_id", AUTHOR).append("username", "author").append("email", "a@x"), "users");
		this.mongo.insert(new Document("_id", VOTER1).append("username", "voter1"), "users");
		this.mongo.insert(new Document("_id", VOTER2).append("username", "voter2"), "users");
		this.mongo.insert(new Document("_id", OTHER_AUTHOR).append("username", "other"), "users");
	}

	private Karma vote(int from, int postUser, int postId, int topicId, int points)
	{
		Karma k = new Karma();
		k.setFromUserId(from);
		k.setPostUserId(postUser);
		k.setPostId(postId);
		k.setTopicId(topicId);
		k.setPoints(points);
		this.store.addKarma(k);
		return k;
	}

	private Document user(int id)
	{
		return this.mongo.findOne(new Query(Criteria.where("_id").is(id)), Document.class, "users");
	}

	@Test
	void isRegisteredInStores()
	{
		assertSame(this.store, Stores.karma());
	}

	@Test
	void votingUpdatesPostAndUserKarma()
	{
		assertTrue(this.store.userCanAddKarma(VOTER1, 100));

		// voter1: 5 and 3 on two posts (avg 4), voter2: 1 (avg 1) -> user karma (4 + 1) / 2 = 2.5
		this.vote(VOTER1, AUTHOR, 100, 7, 5);
		this.vote(VOTER1, AUTHOR, 101, 7, 3);
		this.vote(VOTER2, AUTHOR, 100, 7, 1);

		assertFalse(this.store.userCanAddKarma(VOTER1, 100));
		assertTrue(this.store.userCanAddKarma(VOTER1, 102));

		assertEquals(3, this.store.getPostKarma(100).getKarmaPoints()); // round((5 + 1) / 2)
		assertEquals(3, this.store.getPostKarma(101).getKarmaPoints());
		assertEquals(0, this.store.getPostKarma(999).getKarmaPoints());

		Document author = this.user(AUTHOR);
		assertEquals(2.5, author.getDouble("userKarma"));
		assertEquals("a@x", author.getString("email"), "only the karma field is touched");
		assertEquals(3, this.store.getUserKarma(AUTHOR).getKarmaPoints()); // Math.round(2.5)
		assertEquals(0, this.store.getUserKarma(4242).getKarmaPoints());
	}

	@Test
	void updateChangesPointsAndRecomputeIsExplicit()
	{
		Karma k = this.vote(VOTER1, AUTHOR, 100, 7, 1);
		assertTrue(k.getId() > 0);

		k.setPoints(5);
		this.store.update(k);
		assertEquals(5, this.store.getPostKarma(100).getKarmaPoints());

		this.store.updateUserKarma(AUTHOR);
		assertEquals(5.0, this.user(AUTHOR).getDouble("userKarma"));

		// e.g. after the voted post is deleted
		this.mongo.remove(new Query(), KarmaDocument.class);
		this.store.updateUserKarma(AUTHOR);
		assertEquals(0.0, this.user(AUTHOR).getDouble("userKarma"));
	}

	@Test
	void updateUserKarmaDoesNotCreateUsers()
	{
		this.store.updateUserKarma(4242);
		assertEquals(null, this.user(4242));
	}

	@Test
	void userVotesArePerTopicAndVoter()
	{
		this.vote(VOTER1, AUTHOR, 100, 7, 5);
		this.vote(VOTER1, AUTHOR, 101, 7, 2);
		this.vote(VOTER1, AUTHOR, 200, 8, 4);
		this.vote(VOTER2, AUTHOR, 102, 7, 1);

		Map<?, ?> votes = this.store.getUserVotes(7, VOTER1);
		assertEquals(Map.of(100, 5, 101, 2), votes);
		assertTrue(this.store.getUserVotes(9, VOTER1).isEmpty());
	}

	@Test
	void userTotalKarma()
	{
		this.vote(VOTER1, AUTHOR, 100, 7, 5);
		this.vote(VOTER2, AUTHOR, 100, 7, 2);
		this.vote(AUTHOR, OTHER_AUTHOR, 300, 9, 3);

		User u = new User();
		u.setId(AUTHOR);
		this.store.getUserTotalKarma(u);

		assertEquals(7, u.getKarma().getTotalPoints());
		assertEquals(2, u.getKarma().getVotesReceived());
		assertEquals(3.0, u.getKarma().getKarmaPoints()); // integer division 7 / 2
		assertEquals(1, u.getKarma().getVotesGiven());

		User nobody = new User();
		nobody.setId(VOTER1);
		this.store.getUserTotalKarma(nobody);
		assertEquals(0, nobody.getKarma().getVotesReceived());
		assertEquals(0.0, nobody.getKarma().getKarmaPoints());
		assertEquals(1, nobody.getKarma().getVotesGiven());
	}

	@Test
	void mostRatedUsersByPeriod()
	{
		this.vote(VOTER1, AUTHOR, 100, 7, 5);
		this.vote(VOTER2, AUTHOR, 101, 7, 4);
		this.vote(VOTER1, OTHER_AUTHOR, 300, 9, 2);
		this.vote(VOTER2, OTHER_AUTHOR, 301, 9, 2);
		this.vote(AUTHOR, OTHER_AUTHOR, 302, 9, 1);
		Karma old = this.vote(VOTER1, VOTER2, 400, 10, 5);
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(old.getId())),
			new Update().set("rateDate", new Date(0)), KarmaDocument.class);

		Date from = new Date(System.currentTimeMillis() - 60_000);
		Date to = new Date(System.currentTimeMillis() + 60_000);

		List<?> byTotal = this.store.getMostRatedUserByPeriod(0, from, to, "total");
		assertEquals(2, byTotal.size(), "votes outside the period are ignored");
		User first = (User)byTotal.get(0);
		assertEquals(AUTHOR, first.getId());
		assertEquals("author", first.getUsername());
		assertEquals(9, first.getKarma().getTotalPoints());
		assertEquals(2, first.getKarma().getVotesReceived());
		assertEquals(4.5, first.getKarma().getKarmaPoints());
		assertEquals(1, first.getKarma().getVotesGiven());

		List<?> byVotes = this.store.getMostRatedUserByPeriod(0, from, to, "votes_received");
		assertEquals(OTHER_AUTHOR, ((User)byVotes.get(0)).getId());
		assertEquals(3, ((User)byVotes.get(0)).getKarma().getVotesReceived());

		assertThrows(DatabaseException.class, () -> this.store.getMostRatedUserByPeriod(0, to, from, "total"));
	}
}
