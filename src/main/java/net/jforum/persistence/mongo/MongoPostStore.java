package net.jforum.persistence.mongo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jforum.document.PostDocument;
import net.jforum.document.TopicDocument;
import net.jforum.document.repository.PostDocumentRepository;
import net.jforum.entities.KarmaStatus;
import net.jforum.entities.Post;
import net.jforum.persistence.KarmaStore;
import net.jforum.persistence.PostStore;
import net.jforum.search.SearchFacade;
import net.jforum.util.preferences.ConfigKeys;
import net.jforum.util.preferences.SystemGlobals;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
public class MongoPostStore implements PostStore
{
	private final PostDocumentRepository repository;
	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;
	private final MongoUsers users;
	private final ForumAccessResolver forumAccess;
	private final ObjectProvider<KarmaStore> karma;

	public MongoPostStore(PostDocumentRepository repository, MongoTemplate mongo, SequenceGenerator sequences,
		MongoUsers users, ForumAccessResolver forumAccess, ObjectProvider<KarmaStore> karma)
	{
		this.repository = repository;
		this.mongo = mongo;
		this.sequences = sequences;
		this.users = users;
		this.forumAccess = forumAccess;
		this.karma = karma;
	}

	@Override
	public Post selectById(int postId)
	{
		return this.repository.findById(postId).map(d -> this.toPosts(List.of(d)).get(0)).orElseGet(Post::new);
	}

	@Override
	public void delete(Post post)
	{
		this.removePosts(List.of(post));
	}

	@Override
	public void update(Post post)
	{
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(post.getId())),
			new Update()
				.set("topicId", post.getTopicId())
				.set("forumId", post.getForumId())
				.set("enableBbcode", post.isBbCodeEnabled())
				.set("enableHtml", post.isHtmlEnabled())
				.set("enableSmilies", post.isSmiliesEnabled())
				.set("enableSig", post.isSignatureEnabled())
				.set("postEditTime", new Date())
				.set("postEditCount", post.getEditCount() + 1)
				.set("posterIp", post.getUserIp())
				.set("postText", post.getText())
				.set("postSubject", post.getSubject()),
			PostDocument.class);

		SearchFacade.update(post);
	}

	@Override
	public int addNew(Post post)
	{
		PostDocument d = new PostDocument();
		d.id = this.sequences.next("posts");
		d.topicId = post.getTopicId();
		d.forumId = post.getForumId();
		d.userId = post.getUserId();
		d.postTime = post.getTime() != null ? post.getTime() : new Date();
		d.posterIp = post.getUserIp();
		d.enableBbcode = post.isBbCodeEnabled();
		d.enableHtml = post.isHtmlEnabled();
		d.enableSmilies = post.isSmiliesEnabled();
		d.enableSig = post.isSignatureEnabled();
		d.postEditTime = d.postTime;
		d.needModerate = post.isModerate();
		d.attach = post.hasAttachments();
		d.postSubject = post.getSubject();
		d.postText = post.getText();
		this.repository.insert(d);

		post.setId(d.id);
		post.setTime(d.postTime);
		SearchFacade.create(post);
		return d.id;
	}

	@Override
	public List selectAllByTopicByLimit(int topicId, int startFrom, int count)
	{
		Query query = new Query(Criteria.where("topicId").is(topicId).and("needModerate").is(false))
			.with(Sort.by("postTime", "id"))
			.skip(startFrom)
			.limit(count);

		return this.toPosts(this.mongo.find(query, PostDocument.class));
	}

	@Override
	public List selectByUserByLimit(int userId, int startFrom, int count)
	{
		Query query = new Query(Criteria.where("userId").is(userId)
				.and("needModerate").is(false)
				.and("forumId").in(this.forumAccess.allowedForumIds()))
			.with(Sort.by(Sort.Direction.DESC, "id"))
			.skip(startFrom)
			.limit(count);

		return this.toPosts(this.mongo.find(query, PostDocument.class));
	}

	@Override
	public int countUserPosts(int userId)
	{
		return (int)this.mongo.count(new Query(Criteria.where("userId").is(userId)
			.and("needModerate").is(false)
			.and("forumId").in(this.forumAccess.allowedForumIds())), PostDocument.class);
	}

	@Override
	public List selectAllByTopic(int topicId)
	{
		return this.selectAllByTopicByLimit(topicId, 0, 0);
	}

	@Override
	public void deleteByTopic(int topicId)
	{
		Query query = new Query(Criteria.where("topicId").is(topicId));
		query.fields().include("userId");

		List<Post> posts = new ArrayList<>();

		for (PostDocument d : this.mongo.find(query, PostDocument.class)) {
			Post p = new Post();
			p.setId(d.id);
			p.setUserId(d.userId);
			posts.add(p);
		}

		this.removePosts(posts);
	}

	@Override
	public int countPreviousPosts(int postId)
	{
		PostDocument post = this.repository.findById(postId).orElse(null);

		if (post == null) {
			return 0;
		}

		return (int)this.mongo.count(new Query(Criteria.where("topicId").is(post.topicId).and("_id").lte(postId)),
			PostDocument.class);
	}

	@Override
	public List selectLatestByForumForRSS(int forumId, int limit)
	{
		Query query = new Query(Criteria.where("forumId").is(forumId)).with(Sort.by(Sort.Direction.DESC, "id")).limit(limit);
		query.fields().include("topicFirstPostId");

		List<Post> l = new ArrayList<>();

		for (PostDocument d : this.firstPosts(this.mongo.find(query, TopicDocument.class), limit)) {
			l.add(this.toRssPost(d, d.postSubject));
		}

		this.fillUsernames(l);
		return l;
	}

	@Override
	public List selectHotForRSS(int limit)
	{
		Query query = new Query().with(Sort.by(Sort.Direction.DESC, "topicFirstPostId")).limit(limit);
		query.fields().include("topicFirstPostId", "topicTitle");

		List<TopicDocument> topics = this.mongo.find(query, TopicDocument.class);
		List<Post> l = new ArrayList<>();

		for (PostDocument d : this.firstPosts(topics, limit)) {
			String title = topics.stream().filter(t -> t.topicFirstPostId == d.id).findFirst().map(t -> t.topicTitle).orElse(null);
			l.add(this.toRssPost(d, title));
		}

		this.fillUsernames(l);
		return l;
	}

	/**
	 * Non moderated first posts of the given topics, keeping the topics ordering. The topics are
	 * fetched with the limit already applied, so moderated first posts may reduce the result size
	 * (the legacy query applied the limit after the join).
	 */
	private List<PostDocument> firstPosts(List<TopicDocument> topics, int limit)
	{
		List<Integer> ids = topics.stream().map(t -> t.topicFirstPostId).toList();
		Map<Integer, PostDocument> posts = new java.util.HashMap<>();

		for (PostDocument d : this.mongo.find(new Query(Criteria.where("_id").in(ids).and("needModerate").is(false)), PostDocument.class)) {
			posts.put(d.id, d);
		}

		List<PostDocument> l = new ArrayList<>();

		for (Integer id : ids) {
			PostDocument d = posts.get(id);

			if (d != null && l.size() < limit) {
				l.add(d);
			}
		}

		return l;
	}

	private Post toRssPost(PostDocument d, String subject)
	{
		Post post = new Post();
		post.setId(d.id);
		post.setSubject(subject);
		post.setText(d.postText);
		post.setTopicId(d.topicId);
		post.setForumId(d.forumId);
		post.setUserId(d.userId);
		post.setTime(d.postTime);
		return post;
	}

	private void fillUsernames(List<Post> posts)
	{
		Set<Integer> ids = new HashSet<>();
		posts.forEach(p -> ids.add(p.getUserId()));
		Map<Integer, String> names = this.users.usernames(ids);
		posts.forEach(p -> p.setPostUsername(names.get(p.getUserId())));
	}

	private void removePosts(List<Post> posts)
	{
		List<Integer> ids = posts.stream().map(Post::getId).toList();
		this.mongo.remove(new Query(Criteria.where("_id").in(ids)), PostDocument.class);
		posts.forEach(SearchFacade::delete);
	}

	List<Post> toPosts(List<PostDocument> docs)
	{
		SimpleDateFormat df = new SimpleDateFormat(SystemGlobals.getValue(ConfigKeys.DATE_TIME_FORMAT));
		KarmaStore karmaStore = this.karma.getIfAvailable();
		List<Post> l = new ArrayList<>();

		for (PostDocument d : docs) {
			Post post = new Post();
			post.setId(d.id);
			post.setTopicId(d.topicId);
			post.setForumId(d.forumId);
			post.setUserId(d.userId);
			post.setTime(d.postTime);
			post.setUserIp(d.posterIp);
			post.setBbCodeEnabled(d.enableBbcode);
			post.setHtmlEnabled(d.enableHtml);
			post.setSmiliesEnabled(d.enableSmilies);
			post.setSignatureEnabled(d.enableSig);
			post.setEditCount(d.postEditCount);
			post.setEditTime(d.postEditTime);
			post.setSubject(d.postSubject);
			post.setText(d.postText);
			post.hasAttachments(d.attach);
			post.setModerate(d.needModerate);
			post.setFormatedTime(d.postTime != null ? df.format(d.postTime) : null);
			post.setKarma(karmaStore != null ? karmaStore.getPostKarma(d.id) : new KarmaStatus(d.id, 0));
			l.add(post);
		}

		this.fillUsernames(l);
		return l;
	}
}
