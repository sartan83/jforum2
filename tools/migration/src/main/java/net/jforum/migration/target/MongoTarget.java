package net.jforum.migration.target;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;

/**
 * Writes to MongoDB through Spring Data's {@link MongoTemplate} (unordered bulk upserts by <code>_id</code>).
 */
public class MongoTarget implements Target
{
	public static final String SEQUENCES = "sequences";

	private final MongoClient client;
	private final MongoTemplate mongo;
	private final GridFSBucket bucket;

	public MongoTarget(String uri, String database)
	{
		this.client = MongoClients.create(uri);
		this.mongo = new MongoTemplate(this.client, database);
		this.bucket = GridFSBuckets.create(this.mongo.getDb(), GRIDFS_BUCKET);
	}

	@Override
	public void drop(Collection<String> collections, boolean gridFs)
	{
		for (String collection : collections) {
			this.mongo.dropCollection(collection);
		}
		if (gridFs) {
			this.bucket.drop();
		}
	}

	@Override
	public void write(String collection, List<Document> documents)
	{
		if (documents.isEmpty()) {
			return;
		}
		ReplaceOptions upsert = new ReplaceOptions().upsert(true);
		List<WriteModel<Document>> models = new ArrayList<>(documents.size());
		for (Document doc : documents) {
			models.add(new ReplaceOneModel<>(Filters.eq("_id", doc.get("_id")), doc, upsert));
		}
		this.mongo.getCollection(collection).bulkWrite(models, new BulkWriteOptions().ordered(false));
	}

	@Override
	public void uploadFile(String filename, Path file, Document metadata)
	{
		List<GridFSFile> previous = new ArrayList<>();
		this.bucket.find(Filters.and(
			Filters.eq("metadata.attachId", metadata.get("attachId")),
			Filters.eq("metadata.thumb", metadata.get("thumb")))).into(previous);
		for (GridFSFile f : previous) {
			this.bucket.delete(f.getObjectId());
		}
		try (InputStream in = Files.newInputStream(file)) {
			this.bucket.uploadFromStream(filename, in, new GridFSUploadOptions().metadata(metadata));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public long measure(String collection, Measure measure)
	{
		return switch (measure.kind()) {
			case DOCUMENTS -> this.mongo.getCollection(collection).countDocuments();
			case FIELD_EXISTS -> this.mongo.getCollection(collection).countDocuments(Filters.exists(measure.field()));
			case ARRAY_SIZE -> {
				List<Document> pipeline = List.of(
					new Document("$group", new Document("_id", null).append("total",
						new Document("$sum", new Document("$size", new Document("$ifNull", List.of("$" + measure.field(), List.of())))))));
				Document result = this.mongo.getCollection(collection).aggregate(pipeline).first();
				yield result == null ? 0 : ((Number)result.get("total")).longValue();
			}
			case GRIDFS_FILES -> this.mongo.getCollection(GRIDFS_BUCKET + ".files").countDocuments(
				Filters.exists("metadata.attachId"));
		};
	}

	@Override
	public long maxId(String collection)
	{
		Document doc = this.mongo.findOne(new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1),
			Document.class, collection);
		return doc != null && doc.get("_id") instanceof Number n ? n.longValue() : 0;
	}

	@Override
	public void initSequence(String collection, long value, boolean exact)
	{
		// int32 like the documents created by SequenceGenerator#next ($inc on a missing field)
		Object v = value <= Integer.MAX_VALUE ? (Object)(int)value : (Object)value;
		Update update = exact ? new Update().set("value", v) : new Update().max("value", v);
		this.mongo.upsert(new Query(Criteria.where("_id").is(collection)), update, SEQUENCES);
	}

	@Override
	public Long sequence(String collection)
	{
		Document doc = this.mongo.findById(collection, Document.class, SEQUENCES);
		return doc == null ? null : ((Number)doc.get("value")).longValue();
	}

	@Override
	public void close()
	{
		this.client.close();
	}
}
