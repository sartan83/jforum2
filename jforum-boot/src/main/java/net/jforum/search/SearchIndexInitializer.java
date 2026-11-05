package net.jforum.search;

import net.jforum.domain.Post;
import net.jforum.domain.Topic;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

/**
 * Replaces the legacy Lucene index: makes sure the MongoDB text indexes used by {@link SearchService} exist
 * (posts: subject weight 2 + text, topics: title). Runs at startup and is idempotent.
 */
@Component
public class SearchIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexInitializer.class);

    private final MongoTemplate mongo;

    public SearchIndexInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        ensureTextIndex(Post.class, TextIndexDefinition.builder().named("post_text").onField("subject", 2F).onField("text").build());
        ensureTextIndex(Topic.class, TextIndexDefinition.builder().named("topic_title").onField("title").build());
    }

    private void ensureTextIndex(Class<?> type, TextIndexDefinition definition) {
        String collection = mongo.getCollectionName(type);
        if (!mongo.collectionExists(collection)) {
            mongo.createCollection(collection);
        }
        for (Document index : mongo.getCollection(collection).listIndexes()) {
            Object key = index.get("key");
            if (key instanceof Document k && k.containsKey("_fts")) {
                return;
            }
        }
        mongo.indexOps(collection).ensureIndex(definition);
        log.info("Created text index on {}", collection);
    }
}
