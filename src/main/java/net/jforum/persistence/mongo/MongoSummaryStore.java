package net.jforum.persistence.mongo;

import static net.jforum.persistence.mongo.LegacyCollections.ID;
import static net.jforum.persistence.mongo.LegacyCollections.intValue;
import static net.jforum.persistence.mongo.LegacyCollections.stringValue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jforum.entities.Post;
import net.jforum.persistence.Stores;
import net.jforum.persistence.SummaryStore;
import net.jforum.persistence.mongo.LegacyCollections.Topic;
import net.jforum.persistence.mongo.LegacyCollections.User;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** Data of the weekly summary e-mail (former GenericSummaryDAO). */
@Component
public class MongoSummaryStore implements SummaryStore
{
	private final MongoTemplate mongo;

	public MongoSummaryStore(MongoTemplate mongo)
	{
		this.mongo = mongo;
	}

	/**
	 * First posts of the topics created between the two dates (inclusive), approved and
	 * active, newest first.
	 */
	public List selectLastPosts(Date firstDate, Date lastDate)
	{
		Query topicsQuery = new Query(Criteria.where(Topic.TIME).gte(firstDate).lte(lastDate));
		topicsQuery.fields().include(Topic.FIRST_POST_ID);

		Set<Integer> firstPostIds = new HashSet<>();

		for (Document t : this.mongo.find(topicsQuery, Document.class, LegacyCollections.TOPICS)) {
			firstPostIds.add(intValue(t, Topic.FIRST_POST_ID));
		}

		List<Post> posts = new ArrayList<>();

		if (firstPostIds.isEmpty()) {
			return posts;
		}

		List<Document> docs = this.mongo.find(new Query(new Criteria().andOperator(
				Criteria.where(ID).in(firstPostIds),
				LegacyCollections.isNotSet(LegacyCollections.Post.NEED_MODERATE),
				LegacyCollections.isSet(LegacyCollections.Post.STATUS)))
			.with(Sort.by(Sort.Direction.DESC, LegacyCollections.Post.TIME, ID)),
			Document.class, LegacyCollections.POSTS);

		Set<Integer> userIds = new HashSet<>();
		docs.forEach(d -> userIds.add(intValue(d, LegacyCollections.Post.USER_ID)));
		Map<Integer, String> usernames = LegacyCollections.usernames(this.mongo, userIds);

		SimpleDateFormat df = new SimpleDateFormat(SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT));

		for (Document d : docs) {
			int userId = intValue(d, LegacyCollections.Post.USER_ID);

			if (!usernames.containsKey(userId)) {
				continue;
			}

			Post post = new Post();
			post.setId(intValue(d, ID));
			post.setTopicId(intValue(d, LegacyCollections.Post.TOPIC_ID));
			post.setForumId(intValue(d, LegacyCollections.Post.FORUM_ID));
			post.setUserId(userId);

			Date postTime = d.getDate(LegacyCollections.Post.TIME);
			post.setTime(postTime);
			post.setSubject(stringValue(d, LegacyCollections.Post.SUBJECT));
			post.setText(stringValue(d, LegacyCollections.Post.TEXT));
			post.setPostUsername(usernames.get(userId));

			if (postTime != null) {
				post.setFormatedTime(df.format(postTime));
			}

			try {
				post.setKarma(Stores.karma().getPostKarma(post.getId()));
			}
			catch (IllegalStateException e) {
				// karma store not available: the summary does not show karma anyway
			}

			posts.add(post);
		}

		return posts;
	}

	/** The e-mail of every user, as the legacy query did (no opt-in flag exists). */
	public List listRecipients()
	{
		Query q = new Query().with(Sort.by(ID));
		q.fields().include(User.EMAIL, User.EMAIL_ALT);

		List<String> recipients = new ArrayList<>();

		for (Document u : this.mongo.find(q, Document.class, LegacyCollections.USERS)) {
			String mail = LegacyCollections.email(u);

			if (mail != null && !mail.trim().equals("")) {
				recipients.add(mail);
			}
		}

		return recipients;
	}
}
