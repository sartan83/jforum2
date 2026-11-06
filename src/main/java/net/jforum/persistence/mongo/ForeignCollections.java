package net.jforum.persistence.mongo;

/**
 * Collections/fields owned by other areas and read here (collection = legacy table without
 * <code>jforum_</code>, field = camelCase of the legacy column).
 */
final class ForeignCollections
{
	static final String USERS = "users";
	static final String USER_NAME = "username";
	static final String USER_KARMA = "userKarma";

	static final String FORUMS = "forums";
	static final String FORUM_NAME = "forumName";
	static final String FORUM_DESC = "forumDesc";

	static final String TOPICS = "topics";
	static final String TOPIC_TITLE = "topicTitle";

	private ForeignCollections() {}
}
