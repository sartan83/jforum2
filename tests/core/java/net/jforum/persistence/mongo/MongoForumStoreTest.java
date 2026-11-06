package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import net.jforum.entities.Forum;
import net.jforum.entities.ForumStats;
import net.jforum.entities.LastPostInfo;
import net.jforum.entities.ModeratorInfo;
import net.jforum.entities.Topic;
import net.jforum.entities.User;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MongoForumStoreTest extends ForumCoreTestSupport
{
	private int categoryId;

	@BeforeEach
	void fixture()
	{
		this.user(1, "Anonymous");
		this.user(2, "alice");
		this.user(3, "bob");
		this.categoryId = this.category("cat");
	}

	@Test
	void forumCrudAndOrdering()
	{
		int a = this.forum(this.categoryId, "A");
		int b = this.forum(this.categoryId, "B");

		assertEquals(List.of(a, b), ids(this.forums.selectAll()));
		Forum fa = this.forums.selectById(a);
		assertEquals("A", fa.getName());
		assertEquals("A description", fa.getDescription());
		assertEquals(this.categoryId, fa.getCategoryId());
		assertEquals(1, fa.getOrder());
		assertEquals(2, this.forums.selectById(b).getOrder());

		Forum moved = this.forums.setOrderDown(fa, this.forums.selectById(b));
		assertEquals(2, moved.getOrder());
		assertEquals(List.of(b, a), ids(this.forums.selectAll()));
		this.forums.setOrderUp(this.forums.selectById(a), this.forums.selectById(b));
		assertEquals(List.of(a, b), ids(this.forums.selectAll()));

		fa = this.forums.selectById(a);
		fa.setName("A2");
		fa.setModerated(true);
		this.forums.update(fa);
		assertEquals("A2", this.forums.selectById(a).getName());
		assertTrue(this.forums.selectById(a).isModerated());

		this.forums.setModerated(this.categoryId, false);
		assertFalse(this.forums.selectById(a).isModerated());

		this.forums.delete(b);
		assertEquals(0, this.forums.selectById(b).getId());
		assertEquals(List.of(a), ids(this.forums.selectAll()));
	}

	@Test
	void countersAndLastPostInfo()
	{
		int f = this.forum(this.categoryId, "F");
		Topic t1 = this.topic(f, 2, "t1", 1_000);
		Topic t2 = this.topic(f, 3, "t2", 2_000);
		int last = this.reply(t1, 3, "r", 3_000);

		Forum forum = this.forums.selectById(f);
		assertEquals(2, forum.getTotalTopics());
		assertEquals(3, forum.getTotalPosts());
		assertEquals(last, forum.getLastPostId());
		assertEquals(2, this.forums.getTotalTopics(f));
		assertEquals(last, this.forums.getMaxPostId(f));
		assertEquals(3, this.forums.getTotalMessages());

		LastPostInfo lpi = this.forums.getLastPostInfo(f);
		assertTrue(lpi.hasInfo());
		assertEquals(last, lpi.getPostId());
		assertEquals(t1.getId(), lpi.getTopicId());
		assertEquals("bob", lpi.getUsername());
		assertEquals(3, lpi.getUserId());
		assertEquals(3_000, lpi.getPostTimeMillis());
		assertEquals(1, lpi.getTopicReplies());

		this.forums.setLastPost(f, t2.getLastPostId());
		this.topics.setLastPostId(t1.getId(), 0);
		lpi = this.forums.getLastPostInfo(f);
		assertEquals(t2.getLastPostId(), lpi.getPostId(), "forum last post consistent with topic t2");

		this.forums.setLastPost(f, 999);
		lpi = this.forums.getLastPostInfo(f);
		assertTrue(lpi.hasInfo(), "inconsistent last post id is fixed");
		assertEquals(last, this.forums.selectById(f).getLastPostId());

		this.forums.incrementTotalTopics(f, 3);
		assertEquals(5, this.forums.selectById(f).getTotalTopics());
		this.forums.decrementTotalTopics(f, 3);
		assertEquals(2, this.forums.selectById(f).getTotalTopics());
		assertEquals(last, this.forums.selectById(f).getLastPostId());

		assertFalse(this.forums.getLastPostInfo(this.forum(this.categoryId, "empty")).hasInfo());
	}

	@Test
	void moveTopicsUpdatesPostsCountersAndModeration()
	{
		int from = this.forum(this.categoryId, "from");
		int to = this.forum(this.categoryId, "to");
		Forum dest = this.forums.selectById(to);
		dest.setModerated(true);
		this.forums.update(dest);

		Topic a = this.topic(from, 2, "a", 1_000);
		Topic b = this.topic(from, 2, "b", 2_000);
		int r = this.reply(a, 3, "r", 3_000);

		this.forums.moveTopics(new String[] { String.valueOf(a.getId()) }, from, to);

		Topic moved = this.topics.selectRaw(a.getId());
		assertEquals(to, moved.getForumId());
		assertEquals(from, moved.getMovedId());
		assertTrue(moved.isModerated());
		assertEquals(to, this.posts.selectById(r).getForumId());
		assertEquals(1, this.forums.selectById(from).getTotalTopics());
		assertEquals(1, this.forums.selectById(to).getTotalTopics());
		assertEquals(b.getLastPostId(), this.forums.selectById(from).getLastPostId());
		assertEquals(r, this.forums.selectById(to).getLastPostId());
		assertEquals(2, this.forums.selectById(to).getTotalPosts());
	}

	@Test
	void unreadTopicsAndBoardStatus()
	{
		int f = this.forum(this.categoryId, "F");
		Topic a = this.topic(f, 2, "a", 1_000);
		Topic b = this.topic(f, 2, "b", 2_000);
		this.reply(a, 3, "r", 5_000);

		List<Topic> unread = this.forums.checkUnreadTopics(f, 1_500);
		assertEquals(List.of(a.getId(), b.getId()), unread.stream().map(Topic::getId).toList());
		assertEquals(new Date(5_000), unread.get(0).getTime());
		assertTrue(this.forums.checkUnreadTopics(f, 5_000).isEmpty());

		ForumStats stats = this.forums.getBoardStatus();
		assertEquals(3, stats.getPosts());
		assertEquals(2, stats.getTopics());
		assertEquals(3, stats.getUsers());
		assertTrue(stats.getPostsPerDay() >= 1);
		assertEquals(0, stats.getUsersPerDay(), 0.001, "3 users / 10 days, integer division as in legacy");
	}

	@Test
	void subscriptionsAndNotifications()
	{
		int f = this.forum(this.categoryId, "F");
		this.forums.subscribeUser(f, 1);
		this.forums.subscribeUser(f, 2);
		this.forums.subscribeUser(f, 3);

		assertTrue(this.forums.isUserSubscribed(f, 3));
		assertFalse(this.forums.isUserSubscribed(f, 4));

		List<User> notified = this.forums.notifyUsers(this.forums.selectById(f), 2);
		assertEquals(List.of(3), notified.stream().map(User::getId).toList());
		assertEquals("en_US", notified.get(0).getLang());

		this.forums.removeSubscription(f, 3);
		assertFalse(this.forums.isUserSubscribed(f, 3));
		this.forums.removeSubscriptionByForum(f);
		assertFalse(this.forums.isUserSubscribed(f, 2));
	}

	@Test
	void moderatorsAndMailIntegration()
	{
		int f = this.forum(this.categoryId, "F");
		this.mongo.insert(new Document("_id", 10).append("groupName", "Moderators"), "groups");
		this.mongo.insert(new Document("_id", 11).append("groupName", "Users"), "groups");
		this.mongo.insert(new Document("_id", 1).append("groupId", 10).append("name", "perm_moderation"), "roles");
		this.mongo.insert(new Document("_id", 2).append("groupId", 10).append("name", "perm_moderation_forums")
			.append("roleValues", List.of(String.valueOf(f))), "roles");
		this.mongo.insert(new Document("_id", 3).append("groupId", 11).append("name", "perm_moderation_forums")
			.append("roleValues", List.of(String.valueOf(f))), "roles");

		List<ModeratorInfo> mods = this.forums.getModeratorList(f);
		assertEquals(1, mods.size());
		assertEquals(10, mods.get(0).getId());
		assertEquals("Moderators", mods.get(0).getName());

		this.mongo.insert(new Document("_id", f).append("forumEmail", "list@example.com"), "mail_integration");
		assertEquals(f, this.forums.discoverForumId("list@example.com"));
		assertEquals(0, this.forums.discoverForumId("none@example.com"));
	}

	@Test
	void installationDataFlowReturnsIdsAndKeepsCounters()
	{
		net.jforum.entities.Category c = new net.jforum.entities.Category();
		c.setName("Category Test");
		c.setOrder(1);
		int categoryId = this.categories.addNew(c);
		assertTrue(categoryId > 0);

		Forum f = new Forum();
		f.setIdCategories(categoryId);
		f.setName("Test Forum");
		f.setDescription("This is a test forum");
		f.setOrder(1);
		int forumId = this.forums.addNew(f);
		assertEquals(categoryId, this.forums.selectById(forumId).getCategoryId());

		User admin = new User();
		admin.setId(2);
		Topic t = new Topic();
		t.setForumId(forumId);
		t.setTitle("Welcome to JForum");
		t.setTime(new Date(1_000));
		t.setType(Topic.TYPE_NORMAL);
		t.setPostedBy(admin);
		int topicId = this.topics.addNew(t);
		t.setId(topicId);
		int postId = this.posts.addNew(post(topicId, forumId, 2, "welcome", 1_000));
		t.setFirstPostId(postId);
		t.setLastPostId(postId);
		this.topics.update(t);
		this.forums.incrementTotalTopics(forumId, 1);
		this.forums.setLastPost(forumId, postId);

		Topic reloaded = this.topics.selectById(topicId);
		int replyId = this.posts.addNew(post(topicId, forumId, 3, "reply", 2_000));
		reloaded.setLastPostId(replyId);
		reloaded.setTotalReplies(reloaded.getTotalReplies() + 1);
		this.topics.update(reloaded);
		this.topics.incrementTotalReplies(topicId);
		this.forums.setLastPost(forumId, replyId);

		Topic listed = (Topic)this.topics.selectAllByForumByLimit(forumId, 0, 10).get(0);
		assertEquals(topicId, listed.getId());
		assertEquals(postId, listed.getFirstPostId());
		assertEquals(replyId, listed.getLastPostId());
		assertEquals(1, listed.getTotalReplies());
		assertEquals("alice", listed.getPostedBy().getUsername());
		assertEquals("bob", listed.getLastPostBy().getUsername());
		Forum stored = this.forums.selectById(forumId);
		assertEquals(1, stored.getTotalTopics());
		assertEquals(2, stored.getTotalPosts());
		assertEquals(replyId, stored.getLastPostId());
	}

	private static List<Integer> ids(List<?> l)
	{
		return l.stream().map(f -> ((Forum)f).getId()).toList();
	}
}
