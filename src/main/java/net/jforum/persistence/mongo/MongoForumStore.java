package net.jforum.persistence.mongo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jforum.SessionFacade;
import net.jforum.document.ForumDocument;
import net.jforum.document.ForumWatchDocument;
import net.jforum.document.PostDocument;
import net.jforum.document.TopicDocument;
import net.jforum.document.repository.ForumDocumentRepository;
import net.jforum.entities.Forum;
import net.jforum.entities.ForumStats;
import net.jforum.entities.LastPostInfo;
import net.jforum.entities.ModeratorInfo;
import net.jforum.entities.Topic;
import net.jforum.entities.User;
import net.jforum.persistence.ForumStore;
import net.jforum.persistence.GroupSecurityStore;
import net.jforum.persistence.Stores;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoForumStore implements ForumStore
{
	private final ForumDocumentRepository repository;
	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;
	private final MongoUsers users;
	private final ObjectProvider<GroupSecurityStore> groupSecurity;

	public MongoForumStore(ForumDocumentRepository repository, MongoTemplate mongo, SequenceGenerator sequences,
		MongoUsers users, ObjectProvider<GroupSecurityStore> groupSecurity)
	{
		this.repository = repository;
		this.mongo = mongo;
		this.sequences = sequences;
		this.users = users;
		this.groupSecurity = groupSecurity;
	}

	@Override
	public Forum selectById(int forumId)
	{
		return this.repository.findById(forumId).map(this::toForum).orElseGet(Forum::new);
	}

	@Override
	public List selectAll()
	{
		List<Forum> l = new ArrayList<>();

		for (ForumDocument d : this.repository.findAll(Sort.by("forumOrder", "id"))) {
			l.add(this.toForum(d));
		}

		return l;
	}

	@Override
	public Forum setOrderUp(Forum forum, Forum related)
	{
		return this.changeForumOrder(forum, related);
	}

	@Override
	public Forum setOrderDown(Forum forum, Forum related)
	{
		return this.changeForumOrder(forum, related);
	}

	private Forum changeForumOrder(Forum forum, Forum related)
	{
		int tmpOrder = related.getOrder();
		related.setOrder(forum.getOrder());
		forum.setOrder(tmpOrder);

		this.mongo.updateFirst(byId(forum.getId()), new Update().set("forumOrder", forum.getOrder()), ForumDocument.class);
		this.mongo.updateFirst(byId(related.getId()), new Update().set("forumOrder", related.getOrder()), ForumDocument.class);

		return this.selectById(forum.getId());
	}

	@Override
	public void delete(int forumId)
	{
		this.repository.deleteById(forumId);

		GroupSecurityStore security = this.groupSecurity.getIfAvailable();

		if (security != null) {
			security.deleteForumRoles(forumId);
		}
	}

	@Override
	public void update(Forum forum)
	{
		this.mongo.updateFirst(byId(forum.getId()),
			new Update()
				.set("categoriesId", forum.getCategoryId())
				.set("forumName", forum.getName())
				.set("forumDesc", forum.getDescription())
				.set("moderated", forum.isModerated()),
			ForumDocument.class);
	}

	@Override
	public int addNew(Forum forum)
	{
		ForumDocument max = this.mongo.findOne(new Query().with(Sort.by(Sort.Direction.DESC, "forumOrder")).limit(1),
			ForumDocument.class);

		ForumDocument d = new ForumDocument();
		d.id = this.sequences.next("forums");
		d.categoriesId = forum.getCategoryId();
		d.forumName = forum.getName();
		d.forumDesc = forum.getDescription();
		d.forumOrder = (max == null ? 0 : max.forumOrder) + 1;
		d.moderated = forum.isModerated();
		this.repository.insert(d);

		forum.setId(d.id);
		forum.setOrder(d.forumOrder);
		return d.id;
	}

	@Override
	public void setLastPost(int forumId, int postId)
	{
		this.mongo.updateFirst(byId(forumId), new Update().set("forumLastPostId", postId), ForumDocument.class);
	}

	@Override
	public void incrementTotalTopics(int forumId, int count)
	{
		this.mongo.updateFirst(byId(forumId), new Update().inc("forumTopics", count), ForumDocument.class);
	}

	@Override
	public void decrementTotalTopics(int forumId, int count)
	{
		this.mongo.updateFirst(byId(forumId), new Update().inc("forumTopics", -count), ForumDocument.class);

		if (this.getTotalTopics(forumId) < 1) {
			this.setLastPost(forumId, 0);
		}
	}

	@Override
	public LastPostInfo getLastPostInfo(int forumId)
	{
		return this.getLastPostInfo(forumId, true);
	}

	private LastPostInfo getLastPostInfo(int forumId, boolean tryFix)
	{
		LastPostInfo lpi = new LastPostInfo();
		ForumDocument forum = this.repository.findById(forumId).orElse(null);

		TopicDocument topic = forum == null || forum.forumLastPostId == 0 ? null : this.mongo.findOne(
			new Query(Criteria.where("forumId").is(forumId).and("topicLastPostId").is(forum.forumLastPostId)),
			TopicDocument.class);

		PostDocument post = topic == null ? null : this.mongo.findOne(
			new Query(Criteria.where("_id").is(forum.forumLastPostId).and("forumId").is(forumId).and("needModerate").is(false)),
			PostDocument.class);

		String username = post == null ? null : this.users.username(post.userId);

		if (post != null && username != null) {
			SimpleDateFormat df = new SimpleDateFormat(SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT));
			lpi.setUsername(username);
			lpi.setUserId(post.userId);
			lpi.setPostDate(df.format(post.postTime));
			lpi.setPostId(post.id);
			lpi.setTopicId(post.topicId);
			lpi.setPostTimeMillis(post.postTime.getTime());
			lpi.setTopicReplies(topic.topicReplies);
			lpi.setHasInfo(true);

			if (Stores.topic().selectById(lpi.getTopicId()).getId() == 0) {
				Stores.topic().fixFirstLastPostId(lpi.getTopicId());
			}

			return lpi;
		}

		if (!tryFix) {
			return lpi;
		}

		int postId = this.getMaxPostId(forumId);
		PostDocument latest = this.mongo.findOne(new Query(Criteria.where("forumId").is(forumId).and("needModerate").is(false))
			.with(Sort.by(Sort.Direction.DESC, "topicId")).limit(1), PostDocument.class);

		if (latest != null) {
			Stores.topic().setLastPostId(latest.topicId, postId);
			this.setLastPost(forumId, postId);
		}

		return this.getLastPostInfo(forumId, false);
	}

	/**
	 * Groups having <code>perm_moderation</code> and <code>perm_moderation_forums</code> for the forum.
	 * Reads the security area collections (<code>roles</code>: groupId, name, roleValues;
	 * <code>groups</code>: groupName) through the shared naming conventions.
	 */
	@Override
	public List getModeratorList(int forumId)
	{
		Set<Integer> moderationGroups = new HashSet<>();

		for (Document role : this.mongo.find(new Query(Criteria.where("name").is("perm_moderation")), Document.class, "roles")) {
			moderationGroups.add(MongoUsers.intValue(role.get("groupId")));
		}

		Set<Integer> groupIds = new HashSet<>();
		Query forumRoles = new Query(Criteria.where("name").is("perm_moderation_forums")
			.orOperator(Criteria.where("roleValues").is(String.valueOf(forumId)), Criteria.where("roleValues").is(forumId)));

		for (Document role : this.mongo.find(forumRoles, Document.class, "roles")) {
			int groupId = MongoUsers.intValue(role.get("groupId"));

			if (moderationGroups.contains(groupId)) {
				groupIds.add(groupId);
			}
		}

		List<ModeratorInfo> l = new ArrayList<>();

		for (Document g : this.mongo.find(new Query(Criteria.where("_id").in(groupIds)).with(Sort.by("_id")),
				Document.class, "groups")) {
			ModeratorInfo mi = new ModeratorInfo();
			mi.setId(MongoUsers.intValue(g.get("_id")));
			mi.setName(g.getString("groupName"));
			l.add(mi);
		}

		return l;
	}

	@Override
	public int getTotalMessages()
	{
		return (int)this.mongo.count(new Query(Criteria.where("needModerate").is(false)), PostDocument.class);
	}

	@Override
	public int getTotalTopics(int forumId)
	{
		return (int)this.mongo.count(new Query(Criteria.where("forumId").is(forumId)), TopicDocument.class);
	}

	@Override
	public int getMaxPostId(int forumId)
	{
		PostDocument d = this.mongo.findOne(new Query(Criteria.where("forumId").is(forumId))
			.with(Sort.by(Sort.Direction.DESC, "id")).limit(1), PostDocument.class);
		return d == null ? -1 : d.id;
	}

	@Override
	public void moveTopics(String[] topics, int fromForumId, int toForumId)
	{
		Forum f = this.selectById(toForumId);

		for (String topic : topics) {
			int topicId = Integer.parseInt(topic);

			this.mongo.updateFirst(byId(topicId),
				new Update().set("forumId", toForumId).set("topicMovedId", fromForumId).set("moderated", f.isModerated()),
				TopicDocument.class);
			this.mongo.updateMulti(new Query(Criteria.where("topicId").is(topicId)), new Update().set("forumId", toForumId),
				PostDocument.class);
		}

		this.decrementTotalTopics(fromForumId, topics.length);
		this.incrementTotalTopics(toForumId, topics.length);

		this.setLastPost(fromForumId, this.getMaxPostId(fromForumId));
		this.setLastPost(toForumId, this.getMaxPostId(toForumId));
	}

	@Override
	public List checkUnreadTopics(int forumId, long lastVisit)
	{
		List<Document> pipeline = List.of(
			new Document("$match", new Document("forumId", forumId).append("postTime", new Document("$gt", new Date(lastVisit)))),
			new Document("$group", new Document("_id", "$topicId").append("time", new Document("$max", "$postTime"))),
			new Document("$sort", new Document("_id", 1)));

		List<Topic> l = new ArrayList<>();

		for (Document d : this.mongo.getCollection("posts").aggregate(pipeline)) {
			Topic t = new Topic();
			t.setId(MongoUsers.intValue(d.get("_id")));
			t.setTime(d.getDate("time"));
			l.add(t);
		}

		return l;
	}

	@Override
	public void setModerated(int categoryId, boolean status)
	{
		this.mongo.updateMulti(new Query(Criteria.where("categoriesId").is(categoryId)), new Update().set("moderated", status),
			ForumDocument.class);
	}

	@Override
	public ForumStats getBoardStatus()
	{
		ForumStats fs = new ForumStats();
		fs.setPosts(this.getTotalMessages());
		fs.setUsers(this.users.count());
		fs.setTopics((int)this.mongo.getCollection("topics").countDocuments());

		double postPerDay = 0;
		double topicPerDay = 0;
		double userPerDay = 0;

		PostDocument first = this.mongo.findOne(new Query(Criteria.where("postTime").gt(new Date(0)))
			.with(Sort.by("postTime")).limit(1), PostDocument.class);

		if (first != null) {
			Date today = new Date();
			postPerDay = fs.getPosts() / daysUntilToday(today, first.postTime);

			if (fs.getPosts() > 0 && postPerDay < 1) {
				postPerDay = 1;
			}

			topicPerDay = fs.getTopics() / daysUntilToday(today, first.postTime);

			Date firstUser = this.users.firstRegistrationDate();
			userPerDay = firstUser != null ? fs.getUsers() / daysUntilToday(today, firstUser) : 0;
		}

		fs.setPostsPerDay(postPerDay);
		fs.setTopicsPerDay(topicPerDay);
		fs.setUsersPerDay(userPerDay);
		return fs;
	}

	private static int daysUntilToday(Date today, Date from)
	{
		int days = (int)((today.getTime() - from.getTime()) / (24 * 60 * 60 * 1000));
		return days == 0 ? 1 : days;
	}

	@Override
	public List notifyUsers(Forum forum)
	{
		return this.notifyUsers(forum, SessionFacade.getUserSession().getUserId());
	}

	List notifyUsers(Forum forum, int posterId)
	{
		int anonUser = SystemGlobals.getIntValue(ConfigKeys.ANONYMOUS_USER_ID);

		Set<Integer> ids = new HashSet<>();

		for (ForumWatchDocument w : this.mongo.find(new Query(Criteria.where("forumId").is(forum.getId())
				.and("userId").nin(posterId, anonUser)), ForumWatchDocument.class)) {
			ids.add(w.userId);
		}

		List<User> l = new ArrayList<>();

		for (Document u : this.users.findByIds(ids)) {
			int notifyAlways = MongoUsers.intValue(u.get("userNotifyAlways"));

			if (notifyAlways == 0 || notifyAlways == 1) {
				User user = new User();
				user.setId(MongoUsers.intValue(u.get("_id")));
				user.setEmail(u.getString("userEmail"));
				user.setUsername(u.getString("username"));
				user.setLang(u.getString("userLang"));
				user.setNotifyAlways(notifyAlways == 1);
				user.setNotifyText(MongoUsers.boolValue(u.get("userNotifyText")));
				l.add(user);
			}
		}

		return l;
	}

	@Override
	public void subscribeUser(int forumId, int userId)
	{
		ForumWatchDocument d = new ForumWatchDocument();
		d.forumId = forumId;
		d.userId = userId;
		this.mongo.insert(d);
	}

	@Override
	public boolean isUserSubscribed(int forumId, int userId)
	{
		return this.mongo.exists(watch(forumId, userId), ForumWatchDocument.class);
	}

	@Override
	public void removeSubscription(int forumId, int userId)
	{
		this.mongo.remove(watch(forumId, userId), ForumWatchDocument.class);
	}

	@Override
	public void removeSubscriptionByForum(int forumId)
	{
		this.mongo.remove(new Query(Criteria.where("forumId").is(forumId)), ForumWatchDocument.class);
	}

	/**
	 * Reads <code>mail_integration</code> (owned by the mail integration area): <code>_id</code> = forum id.
	 */
	@Override
	public int discoverForumId(String listEmail)
	{
		Document d = this.mongo.findOne(new Query(Criteria.where("forumEmail").is(listEmail)), Document.class,
			"mail_integration");

		if (d == null) {
			return 0;
		}

		return MongoUsers.intValue(d.containsKey("forumId") ? d.get("forumId") : d.get("_id"));
	}

	private Forum toForum(ForumDocument d)
	{
		Forum f = new Forum();
		f.setId(d.id);
		f.setIdCategories(d.categoriesId);
		f.setName(d.forumName);
		f.setDescription(d.forumDesc);
		f.setOrder(d.forumOrder);
		f.setTotalTopics(d.forumTopics);
		f.setLastPostId(d.forumLastPostId);
		f.setModerated(d.moderated);
		f.setTotalPosts((int)this.mongo.count(new Query(Criteria.where("forumId").is(d.id)), PostDocument.class));
		return f;
	}

	private static Query byId(int id)
	{
		return new Query(Criteria.where("_id").is(id));
	}

	private static Query watch(int forumId, int userId)
	{
		return new Query(Criteria.where("forumId").is(forumId).and("userId").is(userId));
	}
}
