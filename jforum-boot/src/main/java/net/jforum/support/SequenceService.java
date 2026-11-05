package net.jforum.support;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Integer id generator backed by the "counters" collection, so documents keep the numeric ids that
 * legacy URLs (/posts/list/12.page) and the SQL Server migration rely on.
 */
@Service
public class SequenceService {

    public static final String COLLECTION = "counters";

    private final MongoTemplate mongo;

    public SequenceService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public int next(String sequence) {
        Document doc = mongo.findAndModify(
            Query.query(Criteria.where("_id").is(sequence)),
            new Update().inc("seq", 1),
            FindAndModifyOptions.options().returnNew(true).upsert(true),
            Document.class, COLLECTION);
        return ((Number) doc.get("seq")).intValue();
    }

    /** Makes sure the next generated id is greater than {@code atLeast} (used after imports/seeding). */
    public void ensureAtLeast(String sequence, int atLeast) {
        mongo.upsert(Query.query(Criteria.where("_id").is(sequence)), new Update().max("seq", atLeast), COLLECTION);
    }
}
