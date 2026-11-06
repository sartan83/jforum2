package net.jforum.persistence.mongo;

import static net.jforum.persistence.mongo.LegacyFixtures.at;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.jforum.boot.JForumApplication;
import net.jforum.entities.Post;
import net.jforum.search.SearchArgs;
import net.jforum.search.SearchFacade;
import net.jforum.search.SearchPost;
import net.jforum.search.SearchResult;
import net.jforum.support.MongoTestSupport;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(classes = JForumApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MongoSearchManagerTest extends MongoTestSupport
{
	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private MongoSearchManager search;

	private String topicsPerPage;

	@BeforeEach
	void setUp()
	{
		this.topicsPerPage = SystemGlobals.getValue(ConfigKeys.TOPICS_PER_PAGE);
		SystemGlobals.setValue(ConfigKeys.TOPICS_PER_PAGE, "2");

		LegacyFixtures f = new LegacyFixtures(this.mongo);
		f.clearAll();
		this.search.init();

		f.user(1, "admin", "admin@example.com");
		f.user(2, "alice", "alice@example.com");

		f.post(1, 10, 1, 1, at(1, 10), "Welcome", "Welcome to the mongodb forum");
		f.post(2, 10, 1, 2, at(2, 10), "Re: Welcome", "Thanks, mongodb is great");
		f.post(3, 20, 2, 2, at(3, 10), "Java question", "How do I configure spring with mongodb");
		f.post(4, 20, 2, 1, at(4, 10), "Re: Java question", "Use spring boot");
		f.post(5, 30, 2, 2, at(5, 10), "Mongodb mongodb mongodb", "mongodb everywhere: mongodb mongodb");
		f.pendingPost(6, 30, 2, 2, at(6, 10), "pending mongodb", "waiting for moderation mongodb spring");
	}

	@AfterEach
	void tearDown()
	{
		SystemGlobals.setValue(ConfigKeys.TOPICS_PER_PAGE, this.topicsPerPage);
	}

	@Test
	void registeredInTheSearchFacadeWithATextIndex()
	{
		assertSame(this.search, SearchFacade.manager());
		assertTrue(this.search.hasTextIndex());

		this.search.init();
		assertTrue(this.search.hasTextIndex(), "init is idempotent");
	}

	@Test
	void anyKeywordMatchesWithPagingOrderedByTime()
	{
		SearchArgs args = args("spring welcome", "time", "DESC");

		SearchResult page1 = this.search.search(args);
		assertEquals(4, page1.numberOfHits(), "pending posts are not searchable");
		assertEquals(List.of(4, 3), ids(page1));

		args.startFetchingAtRecord(2);
		assertEquals(List.of(2, 1), ids(this.search.search(args)));

		args.startFetchingAtRecord(4);
		assertTrue(this.search.search(args).records().isEmpty());

		SearchArgs asc = args("spring welcome", "time", "ASC");
		assertEquals(List.of(1, 2), ids(this.search.search(asc)));
	}

	@Test
	void allKeywordsMustMatch()
	{
		SearchArgs args = args("spring mongodb", "time", "DESC");
		args.matchAllKeywords();

		SearchResult result = this.search.search(args);
		assertEquals(1, result.numberOfHits());
		assertEquals(List.of(3), ids(result));
	}

	@Test
	void relevanceOrdering()
	{
		SearchArgs args = args("mongodb", "relevance", "DESC");

		SearchResult result = this.search.search(args);
		assertEquals(4, result.numberOfHits());
		assertEquals(5, ids(result).get(0), "the post repeating the keyword scores first");
	}

	@Test
	void forumAuthorAndDateFilters()
	{
		SearchArgs forum = args("mongodb", "time", "DESC");
		forum.setForumId(1);
		assertEquals(List.of(2, 1), ids(this.search.search(forum)));

		SearchArgs author = args("mongodb spring", "time", "DESC");
		author.setAuthor(2);
		SearchResult byAlice = this.search.search(author);
		assertEquals(3, byAlice.numberOfHits());
		assertEquals(List.of(5, 3), ids(byAlice));

		SearchArgs dates = args("mongodb spring", "time", "DESC");
		dates.setDateRange(at(2, 0), at(3, 23));
		assertEquals(List.of(3, 2), ids(this.search.search(dates)));
	}

	@Test
	void resultsArePostsWithAuthorNames()
	{
		SearchArgs args = args("configure", "time", "DESC");
		List<Post> posts = this.search.search(args).records();

		assertEquals(1, posts.size());
		Post p = posts.get(0);
		assertTrue(p instanceof SearchPost);
		assertEquals(3, p.getId());
		assertEquals(20, p.getTopicId());
		assertEquals(2, p.getForumId());
		assertEquals(2, p.getUserId());
		assertEquals("alice", p.getPostUsername());
		assertEquals("Java question", p.getSubject());
		assertEquals("How do I configure spring with mongodb", p.getText());
		assertEquals(at(3, 10), p.getTime());
	}

	@Test
	void subjectIsSearchedToo()
	{
		assertEquals(List.of(4, 3), ids(this.search.search(args("question", "time", "DESC"))));
	}

	@Test
	void findDocumentByPostIdExposesTheLegacyFields()
	{
		MongoSearchManager.SearchDocument doc = (MongoSearchManager.SearchDocument)this.search.findDocumentByPostId(3);

		assertEquals("3", doc.get("post.id"));
		assertEquals("20", doc.get("topic.id"));
		assertEquals("2", doc.get("forum.id"));
		assertEquals("2", doc.get("user.id"));
		assertEquals(14, doc.get("date").length());

		assertNull(this.search.findDocumentByPostId(6), "pending posts are not indexed");
		assertNull(this.search.findDocumentByPostId(999));
		assertEquals(5, this.search.countSearchablePosts());
	}

	private static SearchArgs args(String keywords, String orderBy, String orderDir)
	{
		SearchArgs args = new SearchArgs();
		args.setKeywords(keywords);
		args.setOrderBy(orderBy);
		args.setOrderDir(orderDir);
		return args;
	}

	private static List<Integer> ids(SearchResult result)
	{
		return ((List<Post>)result.records()).stream().map(Post::getId).toList();
	}
}
