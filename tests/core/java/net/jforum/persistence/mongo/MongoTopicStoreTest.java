package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import net.jforum.entities.Poll;
import net.jforum.entities.PollOption;
import net.jforum.entities.Topic;
import net.jforum.entities.User;
import net.jforum.search.SearchArgs;
import net.jforum.search.SearchResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class MongoTopicStoreTest extends ForumCoreTestSupport
{
	private int forumId;

	@BeforeEach
	void fixture()
	{
		this.user(1, "Anonymous");
		this.user(2, "alice");
		this.user(3, "bob");
		this.forumId = this.forum(this.category("cat"), "general");
	}

	@Test
	void listingOrdersByTypeThenLastPostAndPaginates()
	{
		Topic t1 = this.topic(this.forumId, 2, "first", 1_000);
		Topic t2 = this.topic(this.forumId, 3, "second", 2_000);
		Topic sticky = this.topic(this.forumId, 2, "sticky", 500);
		this.topics.update(withType(sticky.getId(), Topic.TYPE_STICKY));
		this.reply(t1, 3, "reply to first", 3_000);

		assertEquals(List.of(sticky.getId(), t1.getId(), t2.getId()), ids(this.topics.selectAllByForum(this.forumId)));
		assertEquals(List.of(t1.getId(), t2.getId()), ids(this.topics.selectAllByForumByLimit(this.forumId, 1, 2)));
		assertEquals(List.of(t2.getId()), ids(this.topics.selectAllByForumByLimit(this.forumId, 2, 10)));

		Topic listed = (Topic)this.topics.selectAllByForumByLimit(this.forumId, 1, 1).get(0);
		assertEquals("alice", listed.getPostedBy().getUsername());
		assertEquals("bob", listed.getLastPostBy().getUsername());
		assertEquals(new Date(3_000), listed.getLastPostDate());
		assertNotNull(listed.getLastPostTime());
		assertEquals(1, listed.getTotalReplies());
	}

	@Test
	void listingHidesTopicsWhoseLastPostIsModeratedAndIncludesMovedTopics()
	{
		Topic visible = this.topic(this.forumId, 2, "visible", 1_000);
		Topic pending = this.topic(this.forumId, 2, "pending", 2_000);
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(pending.getFirstPostId())),
			new Update().set("needModerate", true), "posts");

		assertEquals(List.of(visible.getId()), ids(this.topics.selectAllByForum(this.forumId)));
		assertEquals(pending.getId(), this.topics.selectById(pending.getId()).getId());

		int other = this.forum(this.category("other"), "other");
		this.forums.moveTopics(new String[] { String.valueOf(visible.getId()) }, this.forumId, other);
		assertEquals(List.of(visible.getId()), ids(this.topics.selectAllByForum(other)));
		assertEquals(List.of(visible.getId()), ids(this.topics.selectAllByForum(this.forumId)), "moved topic still listed in the source forum");
	}

	@Test
	void selectByIdAndRaw()
	{
		Topic t = this.topic(this.forumId, 2, "hello", 1_000);

		Topic full = this.topics.selectById(t.getId());
		assertEquals("hello", full.getTitle());
		assertEquals(this.forumId, full.getForumId());
		assertEquals(t.getFirstPostId(), full.getFirstPostId());
		assertEquals("alice", full.getPostedBy().getUsername());
		assertEquals("alice", full.getLastPostBy().getUsername());

		Topic raw = this.topics.selectRaw(t.getId());
		assertEquals("hello", raw.getTitle());
		assertEquals(2, raw.getPostedBy().getId());
		assertEquals(new Date(1_000), raw.getTime());

		assertEquals(0, this.topics.selectById(999).getId());
		assertEquals(0, this.topics.selectRaw(999).getId());

		List<Map> titles = this.topics.selectTopicTitlesByIds(List.of(t.getId(), 999));
		assertEquals(1, titles.size());
		assertEquals("hello", titles.get(0).get("title"));
		assertEquals(t.getId(), titles.get(0).get("id"));
	}

	@Test
	void countersAndFirstLastPostIds()
	{
		Topic t = this.topic(this.forumId, 2, "t", 1_000);
		int r1 = this.reply(t, 3, "r1", 2_000);
		int r2 = this.reply(t, 2, "r2", 3_000);

		this.topics.incrementTotalViews(t.getId());
		this.topics.incrementTotalViews(t.getId());
		this.topics.decrementTotalReplies(t.getId());
		Topic raw = this.topics.selectRaw(t.getId());
		assertEquals(2, raw.getTotalViews());
		assertEquals(1, raw.getTotalReplies());

		assertEquals(3, this.topics.getTotalPosts(t.getId()));
		assertEquals(t.getFirstPostId(), this.topics.getMinPostId(t.getId()));
		assertEquals(r2, this.topics.getMaxPostId(t.getId()));
		assertEquals(-1, this.topics.getMaxPostId(999));

		this.topics.setFirstPostId(t.getId(), r1);
		this.topics.setLastPostId(t.getId(), r1);
		assertEquals(r1, this.topics.selectRaw(t.getId()).getFirstPostId());
		this.topics.fixFirstLastPostId(t.getId());
		raw = this.topics.selectRaw(t.getId());
		assertEquals(t.getFirstPostId(), raw.getFirstPostId());
		assertEquals(r2, raw.getLastPostId());
	}

	@Test
	void recentHottestAndUserTopics()
	{
		int other = this.forum(this.category("c2"), "hidden");
		Topic a = this.topic(this.forumId, 2, "a", 1_000);
		Topic b = this.topic(this.forumId, 3, "b", 2_000);
		Topic c = this.topic(other, 2, "c", 3_000);
		this.reply(a, 3, "bump", 4_000);
		this.topics.incrementTotalViews(b.getId());
		this.topics.incrementTotalViews(b.getId());
		this.topics.incrementTotalViews(c.getId());

		assertEquals(List.of(a.getId(), c.getId()), ids(this.topics.selectRecentTopics(2)));
		assertEquals(List.of(b.getId(), c.getId(), a.getId()), ids(this.topics.selectHottestTopics(10)));

		this.allowForums(this.forumId);
		assertEquals(List.of(a.getId()), ids(this.topics.selectByUserByLimit(2, 0, 10)));
		assertEquals(1, this.topics.countUserTopics(2));

		this.allowForums(this.forumId, other);
		assertEquals(List.of(a.getId(), c.getId()), ids(this.topics.selectByUserByLimit(2, 0, 10)));
		assertEquals(List.of(c.getId()), ids(this.topics.selectByUserByLimit(2, 1, 10)));
		assertEquals(2, this.topics.countUserTopics(2));
	}

	@Test
	void subscriptionsAndNotifications()
	{
		Topic t = this.topic(this.forumId, 2, "watched", 1_000);
		User bob = new User();
		bob.setId(3);
		User anon = new User();
		anon.setId(1);
		this.topics.subscribeUsers(t.getId(), List.of(bob, anon));
		this.topics.subscribeUser(t.getId(), 2);

		assertTrue(this.topics.isUserSubscribed(t.getId(), 3));
		assertFalse(this.topics.isUserSubscribed(t.getId(), 4));

		List<User> notified = this.topics.notifyUsers(t, 2);
		assertEquals(List.of("bob"), notified.stream().map(User::getUsername).toList());
		assertEquals("bob@example.com", notified.get(0).getEmail());

		assertTrue(this.topics.notifyUsers(t, 2).isEmpty(), "bob was marked unread and is not notified again");
		this.topics.updateReadStatus(t.getId(), 3, true);
		assertEquals(1, this.topics.notifyUsers(t, 2).size());

		this.topics.removeSubscription(t.getId(), 3);
		assertFalse(this.topics.isUserSubscribed(t.getId(), 3));
		this.topics.removeSubscriptionByTopic(t.getId());
		assertFalse(this.topics.isUserSubscribed(t.getId(), 2));
	}

	@Test
	void lockModerationAndPosters()
	{
		Topic a = this.topic(this.forumId, 2, "a", 1_000);
		Topic b = this.topic(this.forumId, 3, "b", 2_000);
		this.reply(a, 3, "r", 3_000);

		this.topics.lockUnlock(new int[] { a.getId(), b.getId() }, Topic.STATUS_LOCKED);
		assertEquals(Topic.STATUS_LOCKED, this.topics.selectRaw(a.getId()).getStatus());
		assertEquals(Topic.STATUS_LOCKED, this.topics.selectRaw(b.getId()).getStatus());

		this.topics.setModerationStatus(this.forumId, true);
		assertTrue(this.topics.selectRaw(a.getId()).isModerated());
		this.topics.setModerationStatusByTopic(a.getId(), false);
		assertFalse(this.topics.selectRaw(a.getId()).isModerated());
		assertTrue(this.topics.selectRaw(b.getId()).isModerated());

		Map<Integer, User> posters = this.topics.topicPosters(a.getId());
		assertEquals(2, posters.size());
		assertEquals("alice", posters.get(2).getUsername());
		assertEquals("bob@example.com", posters.get(3).getEmail());
	}

	@Test
	void findTopicsByDateRange()
	{
		Topic a = this.topic(this.forumId, 2, "a", 1_000);
		this.topic(this.forumId, 2, "b", 50_000);
		Topic c = this.topic(this.forumId, 2, "c", 2_000);

		SearchArgs args = new SearchArgs();
		args.setDateRange(new Date(500), new Date(10_000));
		SearchResult result = this.topics.findTopicsByDateRange(args);

		assertEquals(2, result.numberOfHits());
		assertEquals(List.of(c.getId(), a.getId()), ids(result.records()));
	}

	@Test
	void deleteCascadesToPostsPollsWatchesAndForumCounters()
	{
		Topic keep = this.topic(this.forumId, 2, "keep", 1_000);
		Topic t = this.topic(this.forumId, 2, "doomed", 2_000);
		this.reply(t, 3, "r", 3_000);
		this.topics.subscribeUser(t.getId(), 3);

		Poll poll = new Poll();
		poll.setTopicId(t.getId());
		poll.setLabel("?");
		PollOption o = new PollOption();
		o.setText("yes");
		poll.addOption(o);
		int pollId = this.polls.addNew(poll);

		assertEquals(2, this.forums.selectById(this.forumId).getTotalTopics());

		this.topics.delete(this.topics.selectRaw(t.getId()), false);

		assertEquals(0, this.topics.selectRaw(t.getId()).getId());
		assertEquals(0, this.topics.getTotalPosts(t.getId()));
		assertEquals(null, this.polls.selectById(pollId));
		assertFalse(this.topics.isUserSubscribed(t.getId(), 3));
		assertEquals(1, this.forums.selectById(this.forumId).getTotalTopics());

		this.topics.deleteTopics(List.of(this.topics.selectRaw(keep.getId())), true);
		assertEquals(1, this.forums.selectById(this.forumId).getTotalTopics(), "moderation delete keeps counters");

		Topic x = this.topic(this.forumId, 2, "x", 4_000);
		this.topics.deleteByForum(this.forumId);
		assertEquals(0, this.topics.selectRaw(x.getId()).getId());
		assertEquals(0, this.forums.getTotalTopics(this.forumId));
		assertEquals(0, this.forums.selectById(this.forumId).getLastPostId());
	}

	private Topic withType(int topicId, int type)
	{
		Topic t = this.topics.selectRaw(topicId);
		t.setType(type);
		return t;
	}

	private static List<Integer> ids(List<?> l)
	{
		return l.stream().map(t -> ((Topic)t).getId()).toList();
	}
}
