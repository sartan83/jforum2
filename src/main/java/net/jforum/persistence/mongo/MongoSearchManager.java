package net.jforum.persistence.mongo;

import static net.jforum.persistence.mongo.LegacyCollections.ID;
import static net.jforum.persistence.mongo.LegacyCollections.intValue;
import static net.jforum.persistence.mongo.LegacyCollections.stringValue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jforum.entities.Post;
import net.jforum.search.SearchArgs;
import net.jforum.search.SearchFields;
import net.jforum.search.SearchManager;
import net.jforum.search.SearchPost;
import net.jforum.search.SearchResult;

import org.apache.log4j.Logger;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Component;

/**
 * Full text search on a MongoDB text index over <code>posts.subject</code> and <code>posts.text</code>,
 * replacing the Lucene index. The index is maintained by MongoDB itself, so create/update/delete are
 * no-ops and <code>search.indexer.implementation</code> is ignored.
 */
@Component
public class MongoSearchManager implements SearchManager
{
	public static final String TEXT_INDEX_NAME = "posts_text_search";

	private static final Logger logger = Logger.getLogger(MongoSearchManager.class);

	private final MongoTemplate mongo;

	public MongoSearchManager(MongoTemplate mongo)
	{
		this.mongo = mongo;
	}

	/** Declares the text index unless the posts collection already has one (only one is allowed). */
	public void init()
	{
		if (this.hasTextIndex()) {
			return;
		}

		logger.info("Creating text index " + TEXT_INDEX_NAME + " on " + LegacyCollections.POSTS);

		this.mongo.indexOps(LegacyCollections.POSTS).ensureIndex(new TextIndexDefinition.TextIndexDefinitionBuilder()
			.named(TEXT_INDEX_NAME)
			.onField(LegacyCollections.Post.SUBJECT)
			.onField(LegacyCollections.Post.TEXT)
			// no stemming / stop words: forums are multilingual, as the legacy StandardAnalyzer setup
			.withDefaultLanguage("none")
			.build());
	}

	public boolean hasTextIndex()
	{
		for (Document index : this.mongo.getCollection(LegacyCollections.POSTS).listIndexes()) {
			Object key = index.get("key");

			if (key instanceof Document k && k.containsKey("_fts")) {
				return true;
			}
		}

		return false;
	}

	public void create(Post post)
	{
	}

	public void update(Post post)
	{
	}

	public void delete(Post p)
	{
	}

	/**
	 * Keywords (any / all), forum, author and date range filters; ordered by relevance
	 * (text score) or by time (post id); one page of <code>topics.per.page</code> records
	 * starting at {@link SearchArgs#startFrom()}.
	 */
	public SearchResult search(SearchArgs args)
	{
		String[] keywords = args.getKeywords();
		Criteria filters = this.filters(args);

		Query query;

		if (keywords.length > 0) {
			TextCriteria text = TextCriteria.forDefaultLanguage();

			if (args.shouldMatchAllKeywords()) {
				for (String keyword : keywords) {
					if (keyword.length() > 0) {
						text.matchingPhrase(keyword);
					}
				}
			}
			else {
				text.matchingAny(keywords);
			}

			TextQuery textQuery = TextQuery.queryText(text);

			if (!"time".equals(args.getOrderBy())) {
				textQuery.sortByScore();
			}

			query = textQuery.addCriteria(filters);
		}
		else {
			query = new Query(filters);
		}

		int total = (int)this.mongo.count(Query.of(query).limit(0).skip(0), LegacyCollections.POSTS);

		if ("time".equals(args.getOrderBy())) {
			query.with(Sort.by("ASC".equals(args.getOrderDir()) ? Sort.Direction.ASC : Sort.Direction.DESC, ID));
		}
		else {
			query.with(Sort.by(Sort.Direction.DESC, ID));
		}

		query.skip(Math.max(args.startFrom(), 0)).limit(args.fetchCount());

		List<Document> docs = this.mongo.find(query, Document.class, LegacyCollections.POSTS);

		return new SearchResult(this.toPosts(docs), total);
	}

	private Criteria filters(SearchArgs args)
	{
		List<Criteria> and = new ArrayList<>();
		and.add(LegacyCollections.isNotSet(LegacyCollections.Post.NEED_MODERATE));

		if (args.getForumId() > 0) {
			and.add(Criteria.where(LegacyCollections.Post.FORUM_ID).is(args.getForumId()));
		}

		if (args.getAuthor() > 0) {
			and.add(Criteria.where(LegacyCollections.Post.USER_ID).is(args.getAuthor()));
		}

		if (args.getFromDate() != null) {
			Criteria date = Criteria.where(LegacyCollections.Post.TIME).gte(args.getFromDate());

			if (args.getToDate() != null) {
				date.lte(args.getToDate());
			}

			and.add(date);
		}

		return new Criteria().andOperator(and);
	}

	private List<Post> toPosts(List<Document> docs)
	{
		Set<Integer> userIds = new HashSet<>();
		docs.forEach(d -> userIds.add(intValue(d, LegacyCollections.Post.USER_ID)));
		Map<Integer, String> usernames = LegacyCollections.usernames(this.mongo, userIds);

		List<Post> posts = new ArrayList<>();

		for (Document d : docs) {
			Post p = new SearchPost();
			p.setId(intValue(d, ID));
			p.setForumId(intValue(d, LegacyCollections.Post.FORUM_ID));
			p.setTopicId(intValue(d, LegacyCollections.Post.TOPIC_ID));
			p.setUserId(intValue(d, LegacyCollections.Post.USER_ID));
			p.setTime(d.getDate(LegacyCollections.Post.TIME));
			p.setText(stringValue(d, LegacyCollections.Post.TEXT));
			p.setSubject(stringValue(d, LegacyCollections.Post.SUBJECT));
			p.setBbCodeEnabled(LegacyCollections.flag(d.get(LegacyCollections.Post.ENABLE_BBCODE)));
			p.setSmiliesEnabled(LegacyCollections.flag(d.get(LegacyCollections.Post.ENABLE_SMILIES)));
			p.setPostUsername(usernames.get(p.getUserId()));
			posts.add(p);
		}

		return posts;
	}

	/**
	 * @return what <code>admin/ajax_is_post_indexed.js</code> reads (<code>doc.get("post.id")</code>,
	 * <code>topic.id</code>, <code>date</code> as yyyyMMddHHmmss), or <code>null</code> if the post
	 * is not searchable
	 */
	public Object findDocumentByPostId(int postId)
	{
		Document d = this.mongo.findOne(new Query(new Criteria().andOperator(
				Criteria.where(ID).is(postId),
				LegacyCollections.isNotSet(LegacyCollections.Post.NEED_MODERATE))),
			Document.class, LegacyCollections.POSTS);

		if (d == null) {
			return null;
		}

		Map<String, String> fields = new HashMap<>();
		fields.put(SearchFields.Keyword.POST_ID, String.valueOf(postId));
		fields.put(SearchFields.Keyword.TOPIC_ID, String.valueOf(intValue(d, LegacyCollections.Post.TOPIC_ID)));
		fields.put(SearchFields.Keyword.FORUM_ID, String.valueOf(intValue(d, LegacyCollections.Post.FORUM_ID)));
		fields.put(SearchFields.Keyword.USER_ID, String.valueOf(intValue(d, LegacyCollections.Post.USER_ID)));

		Date time = d.getDate(LegacyCollections.Post.TIME);
		fields.put(SearchFields.Keyword.DATE, time == null ? "" : new SimpleDateFormat("yyyyMMddHHmmss").format(time));

		return new SearchDocument(fields);
	}

	/** The indexed representation of a post: a read-only field map with the Lucene document <code>get</code> API. */
	public static class SearchDocument
	{
		private final Map<String, String> fields;

		SearchDocument(Map<String, String> fields)
		{
			this.fields = fields;
		}

		public String get(String name)
		{
			return this.fields.get(name);
		}
	}

	public long countSearchablePosts()
	{
		return this.mongo.count(new Query(LegacyCollections.isNotSet(LegacyCollections.Post.NEED_MODERATE)),
			LegacyCollections.POSTS);
	}
}
