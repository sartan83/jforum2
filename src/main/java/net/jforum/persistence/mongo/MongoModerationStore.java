package net.jforum.persistence.mongo;

import static net.jforum.persistence.mongo.LegacyCollections.ID;
import static net.jforum.persistence.mongo.LegacyCollections.intValue;
import static net.jforum.persistence.mongo.LegacyCollections.isSet;
import static net.jforum.persistence.mongo.LegacyCollections.stringValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jforum.entities.ModerationPendingInfo;
import net.jforum.entities.Post;
import net.jforum.entities.TopicModerationInfo;
import net.jforum.persistence.ModerationStore;
import net.jforum.persistence.mongo.LegacyCollections.Category;
import net.jforum.persistence.mongo.LegacyCollections.Forum;
import net.jforum.persistence.mongo.LegacyCollections.Topic;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Posts waiting for moderation (former GenericModerationDAO). Works on the posts, topics, users,
 * forums and categories collections owned by the other areas. As in the legacy implementation,
 * topic/forum counters and last post ids are updated by the caller
 * (<code>view.admin.ModerationAction</code>) through the topic and forum stores.
 */
@Component
public class MongoModerationStore implements ModerationStore
{

	private final MongoTemplate mongo;

	public MongoModerationStore(MongoTemplate mongo)
	{
		this.mongo = mongo;
	}

	/** <code>need_moderate = 0, post_time = now</code>. */
	public void aprovePost(int postId)
	{
		Query q = new Query(Criteria.where(ID).is(postId));
		q.fields().include(LegacyCollections.Post.NEED_MODERATE);
		Document post = this.mongo.findOne(q, Document.class, LegacyCollections.POSTS);

		if (post == null) {
			return;
		}

		this.mongo.updateFirst(new Query(Criteria.where(ID).is(postId)),
			new Update()
				.set(LegacyCollections.Post.NEED_MODERATE,
					LegacyCollections.flagValue(post.get(LegacyCollections.Post.NEED_MODERATE), false))
				.set(LegacyCollections.Post.TIME, new Date()),
			LegacyCollections.POSTS);
	}

	/**
	 * Pending posts of the forum grouped by topic (topic id order, posts by time), each post
	 * with its author's username.
	 */
	public Map topicsByForum(int forumId)
	{
		List<Document> posts = this.mongo.find(new Query(isSet(LegacyCollections.Post.NEED_MODERATE))
			.with(Sort.by(LegacyCollections.Post.TOPIC_ID, LegacyCollections.Post.TIME, ID)),
			Document.class, LegacyCollections.POSTS);

		Set<Integer> topicIds = new HashSet<>();
		Set<Integer> userIds = new HashSet<>();

		for (Document p : posts) {
			topicIds.add(intValue(p, LegacyCollections.Post.TOPIC_ID));
			userIds.add(intValue(p, LegacyCollections.Post.USER_ID));
		}

		Map<Integer, Document> topics = LegacyCollections.byIds(this.mongo, LegacyCollections.TOPICS, topicIds,
			Topic.FORUM_ID, Topic.TITLE, Topic.REPLIES);
		Map<Integer, String> usernames = LegacyCollections.usernames(this.mongo, userIds);

		Map<Integer, TopicModerationInfo> m = new LinkedHashMap<>();

		for (Document p : posts) {
			int topicId = intValue(p, LegacyCollections.Post.TOPIC_ID);
			int userId = intValue(p, LegacyCollections.Post.USER_ID);
			Document topic = topics.get(topicId);

			if (topic == null || intValue(topic, Topic.FORUM_ID) != forumId || !usernames.containsKey(userId)) {
				continue;
			}

			TopicModerationInfo info = m.get(topicId);

			if (info == null) {
				info = new TopicModerationInfo();
				info.setTopicId(topicId);
				info.setTopicReplies(intValue(topic, Topic.REPLIES));
				info.setTopicTitle(stringValue(topic, Topic.TITLE));
				m.put(topicId, info);
			}

			info.addPost(toPost(p, usernames.get(userId)));
		}

		return m;
	}

	private static Post toPost(Document d, String username)
	{
		Post p = new Post();
		p.setPostUsername(username);
		p.setId(intValue(d, ID));
		p.setUserId(intValue(d, LegacyCollections.Post.USER_ID));
		p.setBbCodeEnabled(LegacyCollections.flag(d.get(LegacyCollections.Post.ENABLE_BBCODE)));
		p.setHtmlEnabled(LegacyCollections.flag(d.get(LegacyCollections.Post.ENABLE_HTML)));
		p.setSmiliesEnabled(LegacyCollections.flag(d.get(LegacyCollections.Post.ENABLE_SMILIES)));
		p.setSubject(stringValue(d, LegacyCollections.Post.SUBJECT));
		p.setText(stringValue(d, LegacyCollections.Post.TEXT));
		return p;
	}

	/**
	 * Number of pending posts per forum, grouped by category (categories and forums by id).
	 */
	public List categoryPendingModeration()
	{
		Aggregation aggregation = Aggregation.newAggregation(
			Aggregation.match(isSet(LegacyCollections.Post.NEED_MODERATE)),
			Aggregation.group(LegacyCollections.Post.FORUM_ID).count().as("total"));

		Map<Integer, Integer> totals = new LinkedHashMap<>();

		for (Document d : this.mongo.aggregate(aggregation, LegacyCollections.POSTS, Document.class)) {
			totals.put(intValue(d, ID), intValue(d, "total"));
		}

		Map<Integer, Document> forums = LegacyCollections.byIds(this.mongo, LegacyCollections.FORUMS, totals.keySet(),
			Forum.NAME, Forum.CATEGORY_ID);

		Set<Integer> categoryIds = new HashSet<>();
		forums.values().forEach(f -> categoryIds.add(intValue(f, Forum.CATEGORY_ID)));

		Map<Integer, Document> categories = LegacyCollections.byIds(this.mongo, LegacyCollections.CATEGORIES,
			categoryIds, Category.TITLE);

		List<Document> rows = new ArrayList<>();

		for (Document f : forums.values()) {
			if (categories.containsKey(intValue(f, Forum.CATEGORY_ID))) {
				rows.add(f);
			}
		}

		rows.sort(Comparator.<Document>comparingInt(f -> intValue(f, Forum.CATEGORY_ID))
			.thenComparingInt(f -> intValue(f, ID)));

		List<ModerationPendingInfo> l = new ArrayList<>();
		ModerationPendingInfo info = null;

		for (Document f : rows) {
			int categoryId = intValue(f, Forum.CATEGORY_ID);

			if (info == null || info.getCategoryId() != categoryId) {
				info = new ModerationPendingInfo();
				info.setCategoryId(categoryId);
				info.setCategoryName(stringValue(categories.get(categoryId), Category.TITLE));
				l.add(info);
			}

			int forumId = intValue(f, ID);
			info.addInfo(stringValue(f, Forum.NAME), forumId, totals.get(forumId));
		}

		return l;
	}
}
