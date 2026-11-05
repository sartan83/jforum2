package net.jforum.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Buffers domain objects and writes them with unordered bulk {@code replaceOne(_id, upsert)} operations, so a re-run
 * overwrites the documents of a previous run instead of duplicating them. In dry-run mode nothing is written.
 */
final class BatchWriter<T> {

    private final MongoTemplate mongo;
    private final Class<T> type;
    private final Function<T, Object> idOf;
    private final int batchSize;
    private final boolean dryRun;
    private final List<T> buffer = new ArrayList<>();
    private long count;

    BatchWriter(MongoTemplate mongo, Class<T> type, Function<T, Object> idOf, int batchSize, boolean dryRun) {
        this.mongo = mongo;
        this.type = type;
        this.idOf = idOf;
        this.batchSize = Math.max(1, batchSize);
        this.dryRun = dryRun;
    }

    void add(T document) {
        count++;
        if (dryRun) {
            return;
        }
        buffer.add(document);
        if (buffer.size() >= batchSize) {
            flush();
        }
    }

    void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        BulkOperations ops = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, type);
        for (T doc : buffer) {
            ops.replaceOne(Query.query(Criteria.where("_id").is(idOf.apply(doc))), doc,
                FindAndReplaceOptions.options().upsert());
        }
        ops.execute();
        buffer.clear();
    }

    long count() {
        return count;
    }
}
