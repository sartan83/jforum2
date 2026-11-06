package net.jforum.persistence.mongo;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Read-only access to the <code>users</code> collection (owned by the users area), using the
 * shared naming conventions: <code>_id</code> = user id, camelCase of the legacy column names.
 */
@Component
public class MongoUsers
{
	public static final String COLLECTION = "users";

	private final MongoTemplate mongo;

	public MongoUsers(MongoTemplate mongo)
	{
		this.mongo = mongo;
	}

	public Map<Integer, String> usernames(Collection<Integer> ids)
	{
		Map<Integer, String> names = new HashMap<>();

		if (ids.isEmpty()) {
			return names;
		}

		Query query = new Query(Criteria.where("_id").in(ids));
		query.fields().include("username");

		for (Document d : this.mongo.find(query, Document.class, COLLECTION)) {
			names.put(intValue(d.get("_id")), d.getString("username"));
		}

		return names;
	}

	public String username(int userId)
	{
		return this.usernames(List.of(userId)).get(userId);
	}

	public List<Document> findByIds(Collection<Integer> ids)
	{
		return this.mongo.find(new Query(Criteria.where("_id").in(ids)), Document.class, COLLECTION);
	}

	public int count()
	{
		return (int)this.mongo.getCollection(COLLECTION).countDocuments();
	}

	public Date firstRegistrationDate()
	{
		Query query = new Query(Criteria.where("userRegdate").gt(new Date(0)))
			.with(org.springframework.data.domain.Sort.by("userRegdate"))
			.limit(1);
		query.fields().include("userRegdate");
		Document d = this.mongo.findOne(query, Document.class, COLLECTION);
		return d == null ? null : d.getDate("userRegdate");
	}

	static int intValue(Object o)
	{
		if (o instanceof Number n) {
			return n.intValue();
		}

		if (o instanceof Boolean b) {
			return b ? 1 : 0;
		}

		if (o instanceof String s && !s.isEmpty()) {
			return Integer.parseInt(s);
		}

		return 0;
	}

	static boolean boolValue(Object o)
	{
		return intValue(o) == 1;
	}

	static double doubleValue(Object o)
	{
		return o instanceof Number n ? n.doubleValue() : 0;
	}
}
