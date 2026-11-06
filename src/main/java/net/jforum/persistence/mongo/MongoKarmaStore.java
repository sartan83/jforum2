package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.jforum.document.KarmaDocument;
import net.jforum.document.repository.KarmaDocumentRepository;
import net.jforum.entities.Karma;
import net.jforum.entities.KarmaStatus;
import net.jforum.entities.User;
import net.jforum.exceptions.DatabaseException;
import net.jforum.persistence.KarmaStore;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Karma votes live in <code>karma</code>; the computed user karma is denormalized, as in the legacy
 * <code>jforum_users.user_karma</code> column, into <code>users.userKarma</code> with a targeted <code>$set</code>.
 */
@Component
public class MongoKarmaStore implements KarmaStore
{
	private final KarmaDocumentRepository repository;
	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;

	public MongoKarmaStore(KarmaDocumentRepository repository, MongoTemplate mongo, SequenceGenerator sequences)
	{
		this.repository = repository;
		this.mongo = mongo;
		this.sequences = sequences;
	}

	@Override
	public void addKarma(Karma karma)
	{
		KarmaDocument doc = new KarmaDocument();
		doc.setId(this.sequences.next(KarmaDocument.COLLECTION));
		doc.setPostId(karma.getPostId());
		doc.setPostUserId(karma.getPostUserId());
		doc.setFromUserId(karma.getFromUserId());
		doc.setPoints(karma.getPoints());
		doc.setTopicId(karma.getTopicId());
		doc.setRateDate(new Date());
		this.repository.insert(doc);
		karma.setId(doc.getId());

		this.updateUserKarma(karma.getPostUserId());
	}

	@Override
	public KarmaStatus getUserKarma(int userId)
	{
		KarmaStatus status = new KarmaStatus();
		Query q = new Query(Criteria.where("_id").is(userId));
		q.fields().include(ForeignCollections.USER_KARMA);
		Document user = this.mongo.findOne(q, Document.class, ForeignCollections.USERS);

		if (user != null) {
			status.setKarmaPoints(Math.round(toDouble(user.get(ForeignCollections.USER_KARMA))));
		}

		return status;
	}

	/**
	 * Average, over the voters, of each voter's average rate (<code>KarmaModel.getUserKarmaPoints</code>).
	 */
	@Override
	public void updateUserKarma(int userId)
	{
		Aggregation agg = Aggregation.newAggregation(
			Aggregation.match(Criteria.where("postUserId").is(userId)),
			Aggregation.group("fromUserId").sum("points").as("points").count().as("votes"));

		int totalRecords = 0;
		double totalPoints = 0;

		for (Document d : this.mongo.aggregate(agg, KarmaDocument.COLLECTION, Document.class)) {
			totalPoints += toDouble(d.get("points")) / toDouble(d.get("votes"));
			totalRecords++;
		}

		double karmaPoints = totalRecords == 0 ? 0 : totalPoints / totalRecords;

		this.mongo.updateFirst(new Query(Criteria.where("_id").is(userId)),
			new Update().set(ForeignCollections.USER_KARMA, karmaPoints), ForeignCollections.USERS);
	}

	@Override
	public boolean userCanAddKarma(int userId, int postId)
	{
		return !this.repository.existsByPostIdAndFromUserId(postId, userId);
	}

	@Override
	public KarmaStatus getPostKarma(int postId)
	{
		KarmaStatus karma = new KarmaStatus();
		Aggregation agg = Aggregation.newAggregation(
			Aggregation.match(Criteria.where("postId").is(postId)),
			Aggregation.group("postId").avg("points").as("points"));
		Document d = this.mongo.aggregate(agg, KarmaDocument.COLLECTION, Document.class).getUniqueMappedResult();

		if (d != null) {
			karma.setKarmaPoints(Math.round(toDouble(d.get("points"))));
		}

		return karma;
	}

	@Override
	public void update(Karma karma)
	{
		this.mongo.updateFirst(new Query(Criteria.where("_id").is(karma.getId())),
			new Update().set("points", karma.getPoints()), KarmaDocument.class);
	}

	@Override
	public Map getUserVotes(int topicId, int userId)
	{
		Map<Integer, Integer> m = new HashMap<>();

		for (KarmaDocument doc : this.repository.findByTopicIdAndFromUserId(topicId, userId)) {
			m.put(doc.getPostId(), doc.getPoints());
		}

		return m;
	}

	@Override
	public void getUserTotalKarma(User user)
	{
		user.setKarma(new KarmaStatus());

		Aggregation agg = Aggregation.newAggregation(
			Aggregation.match(Criteria.where("postUserId").is(user.getId())),
			Aggregation.group("postUserId").sum("points").as("points").count().as("votes"));
		Document d = this.mongo.aggregate(agg, KarmaDocument.COLLECTION, Document.class).getUniqueMappedResult();

		if (d != null) {
			user.getKarma().setTotalPoints((int)toDouble(d.get("points")));
			user.getKarma().setVotesReceived((int)toDouble(d.get("votes")));
		}

		if (user.getKarma().getVotesReceived() != 0) {
			// integer division, as in the legacy implementation
			user.getKarma().setKarmaPoints(user.getKarma().getTotalPoints() / user.getKarma().getVotesReceived());
		}

		user.getKarma().setVotesGiven((int)this.repository.countByFromUserId(user.getId()));
	}

	/**
	 * Users who received votes in the period, with total points and votes received.
	 * <code>orderField</code> accepts the legacy SQL aliases (<code>total</code>, <code>votes_received</code>,
	 * <code>user_karma</code>, <code>username</code>); results are sorted descending, as before.
	 */
	@Override
	public List getMostRatedUserByPeriod(int start, Date firstPeriod, Date lastPeriod, String orderField)
	{
		if (firstPeriod.after(lastPeriod)) {
			throw new DatabaseException("First Date needs to be before the Last Date");
		}

		Aggregation agg = Aggregation.newAggregation(
			Aggregation.match(Criteria.where("rateDate").gte(firstPeriod).lte(lastPeriod)),
			Aggregation.group("postUserId").sum("points").as("total").count().as("votesReceived"),
			Aggregation.lookup(ForeignCollections.USERS, "_id", "_id", "user"),
			Aggregation.unwind("user"),
			Aggregation.lookup(KarmaDocument.COLLECTION, "_id", "fromUserId", "given"),
			Aggregation.project("total", "votesReceived")
				.and("user." + ForeignCollections.USER_NAME).as("username")
				.and("user." + ForeignCollections.USER_KARMA).as("userKarma")
				.and("given").size().as("votesGiven"),
			Aggregation.sort(Sort.by(Sort.Order.desc(sortField(orderField)), Sort.Order.asc("_id"))));

		List<User> l = new ArrayList<>();

		for (Document d : this.mongo.aggregate(agg, KarmaDocument.COLLECTION, Document.class)) {
			User user = new User();
			KarmaStatus karma = new KarmaStatus();
			karma.setTotalPoints((int)toDouble(d.get("total")));
			karma.setVotesReceived((int)toDouble(d.get("votesReceived")));
			karma.setKarmaPoints(toDouble(d.get("userKarma")));
			karma.setVotesGiven((int)toDouble(d.get("votesGiven")));
			user.setUsername(d.getString("username"));
			user.setId(((Number)d.get("_id")).intValue());
			user.setKarma(karma);
			l.add(user);
		}

		return l;
	}

	private static String sortField(String orderField)
	{
		if (orderField == null) {
			return "total";
		}

		switch (orderField.trim().toLowerCase()) {
			case "votes_received":
			case "votesreceived":
				return "votesReceived";
			case "user_karma":
			case "userkarma":
				return "userKarma";
			case "votes_given":
			case "votesgiven":
			case "given":
				return "votesGiven";
			case "username":
				return "username";
			default:
				return "total";
		}
	}

	private static double toDouble(Object value)
	{
		return value instanceof Number n ? n.doubleValue() : 0;
	}
}
