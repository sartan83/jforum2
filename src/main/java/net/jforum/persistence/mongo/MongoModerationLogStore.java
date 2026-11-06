package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jforum.document.ModerationLogDocument;
import net.jforum.document.repository.ModerationLogDocumentRepository;
import net.jforum.entities.ModerationLog;
import net.jforum.entities.User;
import net.jforum.persistence.ModerationLogStore;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class MongoModerationLogStore implements ModerationLogStore
{
	static final String COLLECTION = "moderation_log";

	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;
	private final ModerationLogDocumentRepository logs;

	public MongoModerationLogStore(MongoTemplate mongo, SequenceGenerator sequences, ModerationLogDocumentRepository logs)
	{
		this.mongo = mongo;
		this.sequences = sequences;
		this.logs = logs;
	}

	public void add(ModerationLog log)
	{
		ModerationLogDocument d = new ModerationLogDocument();
		d.id = this.sequences.next(COLLECTION);
		d.userId = log.getUser().getId();
		d.logDescription = log.getDescription();
		d.logOriginalMessage = log.getOriginalMessage();
		d.logDate = new Date();
		d.logType = log.getType();
		d.postId = log.getPostId();
		d.topicId = log.getTopicId();
		d.postUserId = log.getPosterUser().getId();
		this.logs.insert(d);

		log.setId(d.id);
	}

	/** Newest first, <code>LIMIT start, count</code>; usernames come from a left join on users. */
	public List selectAll(int start, int count)
	{
		List<ModerationLogDocument> docs = this.mongo.find(new Query()
			.with(Sort.by(Sort.Direction.DESC, "_id"))
			.skip(Math.max(start, 0))
			.limit(count), ModerationLogDocument.class);

		Set<Integer> userIds = new HashSet<>();

		for (ModerationLogDocument d : docs) {
			userIds.add(d.userId);
			userIds.add(d.postUserId);
		}

		Map<Integer, String> usernames = LegacyCollections.usernames(this.mongo, userIds);
		List<ModerationLog> l = new ArrayList<>();

		for (ModerationLogDocument d : docs) {
			ModerationLog log = new ModerationLog();
			log.setId(d.id);
			log.setDescription(d.logDescription);
			log.setOriginalMessage(d.logOriginalMessage);
			log.setType(d.logType);
			log.setDate(d.logDate);
			log.setPostId(d.postId);
			log.setTopicId(d.topicId);

			User user = new User();
			user.setId(d.userId);
			user.setUsername(usernames.get(d.userId));
			log.setUser(user);

			User posterUser = new User();
			posterUser.setId(d.postUserId);
			posterUser.setUsername(usernames.get(d.postUserId));
			log.setPosterUser(posterUser);

			l.add(log);
		}

		return l;
	}

	public int totalRecords()
	{
		return (int)this.logs.count();
	}
}
