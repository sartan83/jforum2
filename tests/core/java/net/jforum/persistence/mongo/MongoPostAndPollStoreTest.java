package net.jforum.persistence.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import net.jforum.entities.Poll;
import net.jforum.entities.PollChanges;
import net.jforum.entities.PollOption;
import net.jforum.entities.Post;
import net.jforum.entities.Topic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MongoPostAndPollStoreTest extends ForumCoreTestSupport
{
	private int forumId;

	@BeforeEach
	void fixture()
	{
		this.user(2, "alice");
		this.user(3, "bob");
		this.forumId = this.forum(this.category("cat"), "general");
	}

	@Test
	void postCrudKeepsTextInThePostDocument()
	{
		Topic t = this.topic(this.forumId, 2, "hello", 1_000);
		Post p = this.posts.selectById(t.getFirstPostId());

		assertEquals("hello", p.getText());
		assertEquals("Re: hello", p.getSubject());
		assertEquals("alice", p.getPostUsername());
		assertEquals("10.0.0.2", p.getUserIp());
		assertEquals(new Date(1_000), p.getTime());
		assertNotNull(p.getFormatedTime());
		assertTrue(p.isBbCodeEnabled());
		assertFalse(p.isModerationNeeded());
		assertEquals(0, p.getEditCount());

		p.setText("edited");
		p.setSubject("new subject");
		p.setHtmlEnabled(false);
		this.posts.update(p);
		Post edited = this.posts.selectById(p.getId());
		assertEquals("edited", edited.getText());
		assertEquals("new subject", edited.getSubject());
		assertFalse(edited.isHtmlEnabled());
		assertEquals(1, edited.getEditCount());
		assertNotNull(edited.getEditTime());

		this.posts.delete(edited);
		assertEquals(0, this.posts.selectById(p.getId()).getId());
	}

	@Test
	void topicPagingByTimeExcludesModeratedPosts()
	{
		Topic t = this.topic(this.forumId, 2, "p0", 1_000);
		int p1 = this.reply(t, 3, "p1", 3_000);
		int p2 = this.reply(t, 2, "p2", 2_000);
		Post pending = post(t.getId(), this.forumId, 3, "pending", 4_000);
		pending.setModerate(true);
		int moderated = this.posts.addNew(pending);

		assertEquals(List.of(t.getFirstPostId(), p2, p1), ids(this.posts.selectAllByTopic(t.getId())));
		assertEquals(List.of(p2, p1), ids(this.posts.selectAllByTopicByLimit(t.getId(), 1, 5)));
		assertEquals(List.of(t.getFirstPostId()), ids(this.posts.selectAllByTopicByLimit(t.getId(), 0, 1)));

		assertEquals(1, this.posts.countPreviousPosts(t.getFirstPostId()));
		assertEquals(3, this.posts.countPreviousPosts(p2));
		assertEquals(4, this.posts.countPreviousPosts(moderated));

		this.posts.deleteByTopic(t.getId());
		assertTrue(this.posts.selectAllByTopic(t.getId()).isEmpty());
		assertEquals(0, this.posts.selectById(moderated).getId());
	}

	@Test
	void userPostsOrderedByIdDescWithinAllowedForums()
	{
		int hidden = this.forum(this.category("c2"), "hidden");
		Topic a = this.topic(this.forumId, 2, "a", 1_000);
		int r = this.reply(a, 2, "r", 2_000);
		Topic h = this.topic(hidden, 2, "h", 3_000);
		this.reply(a, 3, "bob", 4_000);

		this.allowForums(this.forumId);
		assertEquals(List.of(r, a.getFirstPostId()), ids(this.posts.selectByUserByLimit(2, 0, 10)));
		assertEquals(List.of(a.getFirstPostId()), ids(this.posts.selectByUserByLimit(2, 1, 10)));
		assertEquals(2, this.posts.countUserPosts(2));

		this.allowForums(this.forumId, hidden);
		assertEquals(List.of(h.getFirstPostId(), r, a.getFirstPostId()), ids(this.posts.selectByUserByLimit(2, 0, 10)));
		assertEquals(3, this.posts.countUserPosts(2));
		assertEquals(1, this.posts.countUserPosts(3));
	}

	@Test
	void rssSelections()
	{
		Topic a = this.topic(this.forumId, 2, "a", 1_000);
		Topic b = this.topic(this.forumId, 3, "b", 2_000);
		this.reply(a, 3, "reply", 3_000);
		int other = this.forum(this.category("c2"), "other");
		Topic c = this.topic(other, 2, "c", 4_000);

		List<Post> latest = this.posts.selectLatestByForumForRSS(this.forumId, 10);
		assertEquals(List.of(b.getFirstPostId(), a.getFirstPostId()), ids(latest));
		assertEquals("Re: b", latest.get(0).getSubject());
		assertEquals("bob", latest.get(0).getPostUsername());
		assertEquals(1, this.posts.selectLatestByForumForRSS(this.forumId, 1).size());

		List<Post> hot = this.posts.selectHotForRSS(2);
		assertEquals(List.of(c.getFirstPostId(), b.getFirstPostId()), ids(hot));
		assertEquals("c", hot.get(0).getSubject(), "hot RSS uses the topic title as subject");
		assertEquals("alice", hot.get(0).getPostUsername());
	}

	@Test
	void pollCrudAndVoting()
	{
		Topic t = this.topic(this.forumId, 2, "poll topic", 1_000);
		Poll poll = new Poll();
		poll.setTopicId(t.getId());
		poll.setLabel("Favourite db?");
		poll.setLength(7);
		poll.addOption(option("Mongo"));
		poll.addOption(option("SQL Server"));
		int pollId = this.polls.addNew(poll);

		Poll stored = this.polls.selectById(pollId);
		assertEquals("Favourite db?", stored.getLabel());
		assertEquals(7, stored.getLength());
		assertEquals(t.getId(), stored.getTopicId());
		assertNotNull(stored.getStartTime());
		assertEquals(List.of(1, 2), optionIds(stored));

		this.polls.voteOnPoll(pollId, 1, 2, "10.0.0.2");
		this.polls.voteOnPoll(pollId, 1, 3, "10.0.0.3");
		this.polls.voteOnPoll(pollId, 2, 4, "10.0.0.4");
		stored = this.polls.selectById(pollId);
		assertEquals(2, ((PollOption)stored.getOptions().get(0)).getVoteCount());
		assertEquals(1, ((PollOption)stored.getOptions().get(1)).getVoteCount());
		assertEquals(3, stored.getTotalVotes());

		assertTrue(this.polls.hasUserVotedOnPoll(pollId, 3));
		assertFalse(this.polls.hasUserVotedOnPoll(pollId, 5));
		assertTrue(this.polls.hasUserVotedOnPoll(pollId, "10.0.0.4"));
		assertFalse(this.polls.hasUserVotedOnPoll(pollId, "10.0.0.9"));

		Poll changed = this.polls.selectById(pollId);
		changed.setLabel("Favourite database?");
		changed.setLength(14);
		((PollOption)changed.getOptions().get(0)).setText("MongoDB");
		changed.removeOption((PollOption)changed.getOptions().get(1));
		changed.addOption(option("Postgres"));
		PollChanges changes = new PollChanges(this.polls.selectById(pollId), changed);
		assertTrue(changes.hasChanges());
		changed.setChanges(changes);
		this.polls.update(changed);

		stored = this.polls.selectById(pollId);
		assertEquals("Favourite database?", stored.getLabel());
		assertEquals(14, stored.getLength());
		assertEquals(List.of(1, 2), optionIds(stored), "new option ids continue after the max remaining option id");
		assertEquals("MongoDB", ((PollOption)stored.getOptions().get(0)).getText());
		assertEquals(2, ((PollOption)stored.getOptions().get(0)).getVoteCount());
		assertEquals("Postgres", ((PollOption)stored.getOptions().get(1)).getText());

		this.polls.delete(pollId);
		assertNull(this.polls.selectById(pollId));

		poll = new Poll();
		poll.setTopicId(t.getId());
		poll.addOption(option("x"));
		pollId = this.polls.addNew(poll);
		this.polls.deleteByTopicId(t.getId());
		assertNull(this.polls.selectById(pollId));
	}

	private static PollOption option(String text)
	{
		PollOption o = new PollOption();
		o.setText(text);
		return o;
	}

	private static List<Integer> optionIds(Poll poll)
	{
		return ((List<?>)poll.getOptions()).stream().map(o -> ((PollOption)o).getId()).toList();
	}

	private static List<Integer> ids(List<?> l)
	{
		return l.stream().map(p -> ((Post)p).getId()).toList();
	}
}
