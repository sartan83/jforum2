package net.jforum.persistence.mongo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Collection and field names of the documents owned by other areas (posts, topics, users,
 * forums, categories), following the shared naming conventions: legacy table name without the
 * <code>jforum_</code> prefix, camelCase column names, legacy primary key as <code>_id</code>.
 * Legacy TINYINT flags may be stored either as 0/1 or as booleans, so they are read and
 * matched in both forms.
 */
final class LegacyCollections
{
	static final String POSTS = "posts";
	static final String TOPICS = "topics";
	static final String USERS = "users";
	static final String FORUMS = "forums";
	static final String CATEGORIES = "categories";

	static final String ID = "_id";

	static final class Post
	{
		static final String TOPIC_ID = "topicId";
		static final String FORUM_ID = "forumId";
		static final String USER_ID = "userId";
		static final String TIME = "postTime";
		static final String SUBJECT = "subject";
		static final String TEXT = "text";
		static final String NEED_MODERATE = "needModerate";
		static final String STATUS = "status";
		static final String ATTACH = "attach";
		static final String ENABLE_BBCODE = "enableBbcode";
		static final String ENABLE_HTML = "enableHtml";
		static final String ENABLE_SMILIES = "enableSmilies";
		static final String ENABLE_SIG = "enableSig";

		private Post() {}
	}

	static final class Topic
	{
		static final String FORUM_ID = "forumId";
		static final String TITLE = "topicTitle";
		static final String REPLIES = "topicReplies";
		static final String TIME = "topicTime";
		static final String FIRST_POST_ID = "topicFirstPostId";

		private Topic() {}
	}

	static final class User
	{
		static final String USERNAME = "username";
		static final String EMAIL = "userEmail";
		static final String EMAIL_ALT = "email";

		private User() {}
	}

	static final class Forum
	{
		static final String NAME = "forumName";
		static final String CATEGORY_ID = "categoriesId";

		private Forum() {}
	}

	static final class Category
	{
		static final String TITLE = "title";

		private Category() {}
	}

	private LegacyCollections() {}

	static boolean flag(Object value)
	{
		if (value instanceof Boolean b) {
			return b;
		}

		return value instanceof Number n && n.intValue() != 0;
	}

	/** The value to store for a flag, keeping the type already used by the document (boolean or 0/1). */
	static Object flagValue(Object existing, boolean value)
	{
		if (existing instanceof Boolean) {
			return value;
		}

		return value ? 1 : 0;
	}

	static Criteria isSet(String field)
	{
		return Criteria.where(field).in(1, true, 1L);
	}

	static Criteria isNotSet(String field)
	{
		return Criteria.where(field).nin(1, true, 1L);
	}

	static int intValue(Document d, String field)
	{
		Object v = d.get(field);
		return v instanceof Number n ? n.intValue() : 0;
	}

	static String stringValue(Document d, String field)
	{
		Object v = d.get(field);
		return v == null ? null : v.toString();
	}

	static String email(Document user)
	{
		String email = stringValue(user, User.EMAIL);
		return email != null ? email : stringValue(user, User.EMAIL_ALT);
	}

	/** Loads <code>{_id: doc}</code> for the given ids of a collection, with the requested fields only. */
	static Map<Integer, Document> byIds(MongoTemplate mongo, String collection, Collection<Integer> ids, String... fields)
	{
		Map<Integer, Document> m = new HashMap<>();

		if (ids.isEmpty()) {
			return m;
		}

		Query q = new Query(Criteria.where(ID).in(ids));

		if (fields.length > 0) {
			q.fields().include(fields);
		}

		for (Document d : mongo.find(q, Document.class, collection)) {
			m.put(intValue(d, ID), d);
		}

		return m;
	}

	static Map<Integer, String> usernames(MongoTemplate mongo, Collection<Integer> userIds)
	{
		Map<Integer, String> m = new HashMap<>();
		byIds(mongo, USERS, userIds, User.USERNAME).forEach((id, d) -> m.put(id, stringValue(d, User.USERNAME)));
		return m;
	}
}
