package net.jforum.persistence.mongo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jforum.SessionFacade;
import net.jforum.document.PostDocument;
import net.jforum.document.TopicDocument;
import net.jforum.document.TopicWatchDocument;
import net.jforum.document.repository.TopicDocumentRepository;
import net.jforum.entities.KarmaStatus;
import net.jforum.entities.Topic;
import net.jforum.entities.User;
import net.jforum.persistence.Stores;
import net.jforum.persistence.TopicStore;
import net.jforum.search.SearchArgs;
import net.jforum.search.SearchResult;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoTopicStore implements TopicStore
{
	private final TopicDocumentRepository repository;
	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;
	private final MongoUsers users;
	private final ForumAccessResolver forumAccess;

	public MongoTopicStore(TopicDocumentRepository repository, MongoTemplate mongo, SequenceGenerator sequences,
		MongoUsers users, ForumAccessResolver forumAccess)
	{
		this.repository = repository;
		this.mongo = mongo;
		this.sequences = sequences;
		this.users = users;
		this.forumAccess = forumAccess;
	}

	@Override
	public void fixFirstLastPostId(int topicId)
	{
		int first = this.getMinPostId(topicId);
		int last = this.getMaxPostId(topicId);

		this.mongo.updateFirst(byId(topicId),
			new Update().set("topicFirstPostId", first).set("topicLastPostId", last), TopicDocument.class);
	}

	@Override
	public Topic selectById(int topicId)
	{
		List<Topic> l = this.topicsWithLastPost(new Document("_id", topicId), null, 0, 0, false);
		return l.isEmpty() ? new Topic() : l.get(0);
	}

	@Override
	public Topic selectRaw(int topicId)
	{
		return this.repository.findById(topicId).map(MongoTopicStore::toTopic).orElseGet(Topic::new);
	}

	@Override
	public List selectAllByForum(int forumId)
	{
		return this.selectAllByForumByLimit(forumId, 0, 0);
	}

	@Override
	public List selectTopicTitlesByIds(Collection idList)
	{
		List<Integer> ids = new ArrayList<>();

		for (Object o : idList) {
			ids.add(MongoUsers.intValue(o));
		}

		Query query = new Query(Criteria.where("_id").in(ids));
		query.fields().include("topicTitle");

		List<Map<String, Object>> l = new ArrayList<>();

		for (TopicDocument d : this.mongo.find(query, TopicDocument.class)) {
			Map<String, Object> m = new HashMap<>();
			m.put("id", d.id);
			m.put("title", d.topicTitle);
			l.add(m);
		}

		return l;
	}

	@Override
	public List selectAllByForumByLimit(int forumId, int startFrom, int count)
	{
		Document match = new Document("$or", List.of(new Document("forumId", forumId), new Document("topicMovedId", forumId)));
		return this.topicsWithLastPost(match, new Document("topicType", -1).append("topicLastPostId", -1), startFrom, count, true);
	}

	@Override
	public List selectByUserByLimit(int userId, int startFrom, int count)
	{
		Document match = new Document("userId", userId)
			.append("forumId", new Document("$in", this.forumAccess.allowedForumIds()));
		return this.topicsWithLastPost(match, new Document("topicLastPostId", -1), startFrom, count, true);
	}

	@Override
	public int countUserTopics(int userId)
	{
		List<Document> pipeline = List.of(
			new Document("$match", new Document("userId", userId)
				.append("forumId", new Document("$in", this.forumAccess.allowedForumIds()))),
			lookupPost("topicFirstPostId", "firstPost"),
			new Document("$unwind", "$firstPost"),
			new Document("$match", new Document("firstPost.needModerate", new Document("$nin", List.of(1, true, 1L)))),
			new Document("$count", "total"));

		Document d = this.mongo.getCollection("topics").aggregate(pipeline).first();
		return d == null ? 0 : d.getInteger("total");
	}

	@Override
	public void delete(Topic topic, boolean fromModeration)
	{
		this.deleteTopics(List.of(topic), fromModeration);
	}

	@Override
	public void deleteTopics(List topics, boolean fromModeration)
	{
		for (Object o : topics) {
			Topic topic = (Topic)o;

			this.removeSubscriptionByTopic(topic.getId());
			Stores.post().deleteByTopic(topic.getId());
			Stores.poll().deleteByTopicId(topic.getId());
			this.repository.deleteById(topic.getId());

			if (!fromModeration) {
				Stores.forum().decrementTotalTopics(topic.getForumId(), 1);
			}
		}
	}

	@Override
	public void deleteByForum(int forumId)
	{
		Query query = new Query(Criteria.where("forumId").is(forumId));
		query.fields().include("_id");

		List<Topic> topics = new ArrayList<>();

		for (TopicDocument d : this.mongo.find(query, TopicDocument.class)) {
			Topic t = new Topic();
			t.setId(d.id);
			t.setForumId(forumId);
			topics.add(t);
		}

		this.deleteTopics(topics, false);
	}

	@Override
	public void update(Topic topic)
	{
		this.mongo.updateFirst(byId(topic.getId()),
			new Update()
				.set("topicTitle", topic.getTitle())
				.set("topicLastPostId", topic.getLastPostId())
				.set("topicFirstPostId", topic.getFirstPostId())
				.set("topicType", topic.getType())
				.set("moderated", topic.isModerated() ? 1 : 0)
				.set("topicVoteId", topic.getVoteId()),
			TopicDocument.class);
	}

	@Override
	public int addNew(Topic topic)
	{
		TopicDocument d = new TopicDocument();
		d.id = this.sequences.next("topics");
		d.forumId = topic.getForumId();
		d.topicTitle = topic.getTitle();
		d.userId = topic.getPostedBy().getId();
		d.topicTime = topic.getTime();
		d.topicFirstPostId = topic.getFirstPostId();
		d.topicLastPostId = topic.getLastPostId();
		d.topicType = topic.getType();
		d.moderated = topic.isModerated() ? 1 : 0;
		this.repository.insert(d);

		topic.setId(d.id);
		return d.id;
	}

	@Override
	public void incrementTotalViews(int topicId)
	{
		this.inc(topicId, "topicViews", 1);
	}

	@Override
	public void incrementTotalReplies(int topicId)
	{
		this.inc(topicId, "topicReplies", 1);
	}

	@Override
	public void decrementTotalReplies(int topicId)
	{
		this.inc(topicId, "topicReplies", -1);
	}

	@Override
	public void setLastPostId(int topicId, int postId)
	{
		this.mongo.updateFirst(byId(topicId), new Update().set("topicLastPostId", postId), TopicDocument.class);
	}

	@Override
	public void setFirstPostId(int topicId, int postId)
	{
		this.mongo.updateFirst(byId(topicId), new Update().set("topicFirstPostId", postId), TopicDocument.class);
	}

	@Override
	public int getMaxPostId(int topicId)
	{
		PostDocument d = this.mongo.findOne(new Query(Criteria.where("topicId").is(topicId))
			.with(Sort.by(Sort.Direction.DESC, "id")).limit(1), PostDocument.class);
		return d == null ? -1 : d.id;
	}

	@Override
	public int getMinPostId(int topicId)
	{
		PostDocument d = this.mongo.findOne(new Query(Criteria.where("topicId").is(topicId))
			.with(Sort.by("id")).limit(1), PostDocument.class);
		return d == null ? -1 : d.id;
	}

	@Override
	public int getTotalPosts(int topicId)
	{
		return (int)this.mongo.count(new Query(Criteria.where("topicId").is(topicId).and("needModerate").nin(1, true, 1L)),
			PostDocument.class);
	}

	@Override
	public List notifyUsers(Topic topic)
	{
		return this.notifyUsers(topic, SessionFacade.getUserSession().getUserId());
	}

	List notifyUsers(Topic topic, int posterId)
	{
		int anonUser = SystemGlobals.getIntValue(ConfigKeys.ANONYMOUS_USER_ID);

		Map<Integer, Boolean> watches = new HashMap<>();

		for (TopicWatchDocument w : this.mongo.find(new Query(Criteria.where("topicId").is(topic.getId())
				.and("userId").nin(posterId, anonUser)), TopicWatchDocument.class)) {
			watches.put(w.userId, w.isRead != 0);
		}

		List<User> l = new ArrayList<>();

		for (Document u : this.users.findByIds(watches.keySet())) {
			int userId = MongoUsers.intValue(u.get("_id"));

			if (watches.get(userId) || MongoUsers.boolValue(u.get("userNotifyAlways"))) {
				User user = new User();
				user.setId(userId);
				user.setEmail(u.getString("userEmail"));
				user.setUsername(u.getString("username"));
				user.setLang(u.getString("userLang"));
				user.setNotifyText(MongoUsers.boolValue(u.get("userNotifyText")));
				l.add(user);
			}
		}

		this.mongo.updateMulti(new Query(Criteria.where("topicId").is(topic.getId()).and("userId").nin(posterId, anonUser)),
			new Update().set("isRead", 0), TopicWatchDocument.class);

		return l;
	}

	@Override
	public void subscribeUsers(int topicId, List users)
	{
		for (Object o : users) {
			this.subscribeUser(topicId, ((User)o).getId());
		}
	}

	@Override
	public void subscribeUser(int topicId, int userId)
	{
		TopicWatchDocument d = new TopicWatchDocument();
		d.topicId = topicId;
		d.userId = userId;
		d.isRead = 1;
		this.mongo.insert(d);
	}

	@Override
	public boolean isUserSubscribed(int topicId, int userId)
	{
		return this.mongo.exists(watch(topicId, userId), TopicWatchDocument.class);
	}

	@Override
	public void removeSubscription(int topicId, int userId)
	{
		this.mongo.remove(watch(topicId, userId), TopicWatchDocument.class);
	}

	@Override
	public void removeSubscriptionByTopic(int topicId)
	{
		this.mongo.remove(new Query(Criteria.where("topicId").is(topicId)), TopicWatchDocument.class);
	}

	@Override
	public void updateReadStatus(int topicId, int userId, boolean read)
	{
		this.mongo.updateMulti(watch(topicId, userId), new Update().set("isRead", read ? 1 : 0), TopicWatchDocument.class);
	}

	@Override
	public void lockUnlock(int[] topicId, int status)
	{
		List<Integer> ids = new ArrayList<>();

		for (int id : topicId) {
			ids.add(id);
		}

		this.mongo.updateMulti(new Query(Criteria.where("_id").in(ids)), new Update().set("topicStatus", status),
			TopicDocument.class);
	}

	@Override
	public List selectRecentTopics(int limit)
	{
		return this.topicsWithLastPost(new Document(), new Document("topicLastPostId", -1), 0, limit, true);
	}

	@Override
	public List selectHottestTopics(int limit)
	{
		return this.topicsWithLastPost(new Document(), new Document("topicViews", -1).append("_id", -1), 0, limit, true);
	}

	@Override
	public void setModerationStatus(int forumId, boolean status)
	{
		this.mongo.updateMulti(new Query(Criteria.where("forumId").is(forumId)), new Update().set("moderated", status ? 1 : 0),
			TopicDocument.class);
	}

	@Override
	public void setModerationStatusByTopic(int topicId, boolean status)
	{
		this.mongo.updateFirst(byId(topicId), new Update().set("moderated", status ? 1 : 0), TopicDocument.class);
	}

	@Override
	public Map topicPosters(int topicId)
	{
		Set<Integer> ids = new HashSet<>(this.mongo.findDistinct(new Query(Criteria.where("topicId").is(topicId)),
			"userId", PostDocument.class, Integer.class));

		Map<Integer, User> m = new HashMap<>();

		for (Document d : this.users.findByIds(ids)) {
			User u = new User();
			u.setId(MongoUsers.intValue(d.get("_id")));
			u.setUsername(d.getString("username"));
			u.setKarma(new KarmaStatus(u.getId(), MongoUsers.doubleValue(d.get("userKarma"))));
			u.setAvatar(d.getString("userAvatar"));
			u.setAvatarEnabled(MongoUsers.boolValue(d.get("userAllowavatar")));
			u.setRegistrationDate(d.getDate("userRegdate"));
			u.setTotalPosts(MongoUsers.intValue(d.get("userPosts")));
			u.setFrom(d.getString("userFrom"));
			u.setEmail(d.getString("userEmail"));
			u.setRankId(MongoUsers.intValue(d.get("rankId")));
			u.setViewEmailEnabled(MongoUsers.boolValue(d.get("userViewemail")));
			u.setIcq(d.getString("userIcq"));
			u.setAttachSignatureEnabled(MongoUsers.boolValue(d.get("userAttachsig")));
			u.setMsnm(d.getString("userMsnm"));
			u.setYim(d.getString("userYim"));
			u.setWebSite(d.getString("userWebsite"));
			u.setAim(d.getString("userAim"));
			u.setSignature(d.getString("userSig"));
			m.put(u.getId(), u);
		}

		return m;
	}

	@Override
	public SearchResult findTopicsByDateRange(SearchArgs args)
	{
		List<Integer> topicIds = this.mongo.findDistinct(
			new Query(Criteria.where("postTime").gte(args.getFromDate()).lte(args.getToDate())),
			"topicId", PostDocument.class, Integer.class);

		List<Integer> page = new ArrayList<>();

		for (int i = 0; i < topicIds.size(); i++) {
			if (i >= args.startFrom() && i < args.startFrom() + args.fetchCount()) {
				page.add(topicIds.get(i));
			}
		}

		List l = page.isEmpty()
			? new ArrayList()
			: this.topicsWithLastPost(new Document("_id", new Document("$in", page)), new Document("topicLastPostId", -1), 0, 0, true);

		return new SearchResult(l, topicIds.size());
	}

	/**
	 * Equivalent of the legacy <code>topics JOIN posts ON post_id = topic_last_post_id</code> queries
	 * (<code>fillTopicsData</code>): topics without a (non moderated) last post are not returned.
	 * A <code>count</code> of 0 means no limit.
	 */
	private List<Topic> topicsWithLastPost(Document match, Document sort, int startFrom, int count, boolean onlyApproved)
	{
		List<Document> pipeline = new ArrayList<>();
		pipeline.add(new Document("$match", match));
		pipeline.add(lookupPost("topicLastPostId", "lastPost"));
		pipeline.add(new Document("$unwind", "$lastPost"));

		if (onlyApproved) {
			pipeline.add(new Document("$match", new Document("lastPost.needModerate", new Document("$nin", List.of(1, true, 1L)))));
		}

		if (sort != null) {
			pipeline.add(new Document("$sort", sort));
		}

		if (startFrom > 0) {
			pipeline.add(new Document("$skip", startFrom));
		}

		if (count > 0) {
			pipeline.add(new Document("$limit", count));
		}

		SimpleDateFormat df = new SimpleDateFormat(SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT));
		List<Topic> l = new ArrayList<>();
		Set<Integer> userIds = new HashSet<>();

		for (Document d : this.mongo.getCollection("topics").aggregate(pipeline)) {
			Document lastPost = d.get("lastPost", Document.class);
			Topic t = toTopic(this.mongo.getConverter().read(TopicDocument.class, d));

			User last = new User();
			last.setId(MongoUsers.intValue(lastPost.get("userId")));
			t.setLastPostBy(last);
			t.setHasAttach(MongoUsers.boolValue(lastPost.get("attach")));
			t.setFirstPostTime(t.getTime() != null ? df.format(t.getTime()) : null);
			t.setLastPostTime(df.format(lastPost.getDate("postTime")));
			t.setLastPostDate(lastPost.getDate("postTime"));

			userIds.add(t.getPostedBy().getId());
			userIds.add(last.getId());
			l.add(t);
		}

		Map<Integer, String> names = this.users.usernames(userIds);

		for (Topic t : l) {
			t.getPostedBy().setUsername(names.get(t.getPostedBy().getId()));
			t.getLastPostBy().setUsername(names.get(t.getLastPostBy().getId()));
		}

		return l;
	}

	private static Document lookupPost(String localField, String as)
	{
		return new Document("$lookup", new Document("from", "posts")
			.append("localField", localField)
			.append("foreignField", "_id")
			.append("as", as));
	}

	private void inc(int topicId, String field, int delta)
	{
		this.mongo.updateFirst(byId(topicId), new Update().inc(field, delta), TopicDocument.class);
	}

	private static Query byId(int id)
	{
		return new Query(Criteria.where("_id").is(id));
	}

	private static Query watch(int topicId, int userId)
	{
		return new Query(Criteria.where("topicId").is(topicId).and("userId").is(userId));
	}

	static Topic toTopic(TopicDocument d)
	{
		Topic t = new Topic();
		t.setTitle(d.topicTitle);
		t.setId(d.id);
		t.setTime(d.topicTime);
		t.setStatus(d.topicStatus);
		t.setTotalViews(d.topicViews);
		t.setTotalReplies(d.topicReplies);
		t.setFirstPostId(d.topicFirstPostId);
		t.setLastPostId(d.topicLastPostId);
		t.setType(d.topicType);
		t.setForumId(d.forumId);
		t.setModerated(d.moderated != 0);
		t.setVoteId(d.topicVoteId);
		t.setMovedId(d.topicMovedId);

		User user = new User();
		user.setId(d.userId);
		t.setPostedBy(user);
		return t;
	}
}
