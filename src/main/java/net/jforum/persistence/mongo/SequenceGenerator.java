package net.jforum.persistence.mongo;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Generates the numeric identifiers used by JForum (the former SQL
 * auto-increment columns). Sequences live in the <code>sequences</code>
 * collection, one document per collection name: <code>{_id: "posts", value: 42}</code>.
 */
@Component
public class SequenceGenerator
{
	public static final String COLLECTION = "sequences";

	private final MongoTemplate mongo;

	public SequenceGenerator(MongoTemplate mongo)
	{
		this.mongo = mongo;
	}

	public int next(String sequence)
	{
		Document doc = this.mongo.findAndModify(
			new Query(Criteria.where("_id").is(sequence)),
			new Update().inc("value", 1),
			FindAndModifyOptions.options().returnNew(true).upsert(true),
			Document.class,
			COLLECTION);

		return ((Number)doc.get("value")).intValue();
	}

	/**
	 * Makes sure the next generated value is greater than <code>value</code>
	 * (used after importing documents with explicit ids).
	 */
	public void ensureAtLeast(String sequence, int value)
	{
		this.mongo.upsert(
			new Query(Criteria.where("_id").is(sequence)),
			new Update().max("value", value),
			COLLECTION);
	}
}
