package net.jforum.persistence.mongo;

import java.util.ArrayList;
import java.util.List;

import net.jforum.document.BookmarkDocument;
import net.jforum.document.repository.BookmarkDocumentRepository;
import net.jforum.entities.Bookmark;
import net.jforum.entities.BookmarkType;
import net.jforum.exceptions.InvalidBookmarkTypeException;
import net.jforum.persistence.BookmarkStore;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

@Component
public class MongoBookmarkStore implements BookmarkStore
{
	private final BookmarkDocumentRepository repository;
	private final MongoTemplate mongo;
	private final SequenceGenerator sequences;

	public MongoBookmarkStore(BookmarkDocumentRepository repository, MongoTemplate mongo, SequenceGenerator sequences)
	{
		this.repository = repository;
		this.mongo = mongo;
		this.sequences = sequences;
	}

	@Override
	public void add(Bookmark b)
	{
		BookmarkDocument doc = new BookmarkDocument();
		doc.setId(this.sequences.next(BookmarkDocument.COLLECTION));
		doc.setUserId(b.getUserId());
		doc.setRelationId(b.getRelationId());
		doc.setRelationType(b.getRelationType());
		doc.setPublicVisible(b.isPublicVisible());
		doc.setTitle(b.getTitle());
		doc.setDescription(b.getDescription());
		this.repository.insert(doc);
		b.setId(doc.getId());
	}

	@Override
	public void update(Bookmark b)
	{
		this.repository.findById(b.getId()).ifPresent(doc -> {
			doc.setPublicVisible(b.isPublicVisible());
			doc.setTitle(b.getTitle());
			doc.setDescription(b.getDescription());
			this.repository.save(doc);
		});
	}

	@Override
	public void remove(int bookmarkId)
	{
		this.repository.deleteById(bookmarkId);
	}

	@Override
	public List selectByUser(int userId, int relationType)
	{
		if (relationType == BookmarkType.FORUM) {
			return this.selectJoined(userId, relationType, ForeignCollections.FORUMS,
				ForeignCollections.FORUM_NAME, ForeignCollections.FORUM_DESC);
		}
		else if (relationType == BookmarkType.TOPIC) {
			return this.selectJoined(userId, relationType, ForeignCollections.TOPICS,
				ForeignCollections.TOPIC_TITLE, null);
		}
		else if (relationType == BookmarkType.USER) {
			return this.selectJoined(userId, relationType, ForeignCollections.USERS,
				ForeignCollections.USER_NAME, null);
		}

		throw new InvalidBookmarkTypeException("The type " + relationType + " is not a valid bookmark type");
	}

	/**
	 * Inner join with the related collection, ordered by the related name, like the legacy
	 * <code>BookmarkModel.select{Forum,Topic,User}Bookmarks</code> queries. Empty title/description
	 * fall back to the related name/description.
	 */
	private List<Bookmark> selectJoined(int userId, int relationType, String collection,
		String titleField, String descriptionField)
	{
		List<AggregationOperation> ops = List.of(
			Aggregation.match(Criteria.where("userId").is(userId).and("relationType").is(relationType)),
			Aggregation.lookup(collection, "relationId", "_id", "rel"),
			Aggregation.unwind("rel"),
			Aggregation.sort(org.springframework.data.domain.Sort.by("rel." + titleField)));

		List<Bookmark> l = new ArrayList<>();

		for (Document d : this.mongo.aggregate(Aggregation.newAggregation(ops),
				BookmarkDocument.COLLECTION, Document.class)) {
			Bookmark b = toEntity(d);
			Document rel = d.get("rel", Document.class);

			if (b.getTitle() == null || "".equals(b.getTitle())) {
				b.setTitle(rel.getString(titleField));
			}

			if (descriptionField != null && (b.getDescription() == null || "".equals(b.getDescription()))) {
				b.setDescription(rel.getString(descriptionField));
			}

			l.add(b);
		}

		return l;
	}

	@Override
	public List selectByUser(int userId)
	{
		List<Bookmark> l = new ArrayList<>();

		for (BookmarkDocument doc : this.repository.findByUserIdOrderByTitleAsc(userId)) {
			l.add(toEntity(doc));
		}

		return l;
	}

	@Override
	public Bookmark selectById(int bookmarkId)
	{
		return this.repository.findById(bookmarkId).map(MongoBookmarkStore::toEntity).orElse(null);
	}

	@Override
	public Bookmark selectForUpdate(int relationId, int relationType, int userId)
	{
		BookmarkDocument doc = this.repository.findFirstByRelationIdAndRelationTypeAndUserId(
			relationId, relationType, userId);
		return doc == null ? null : toEntity(doc);
	}

	private static Bookmark toEntity(BookmarkDocument doc)
	{
		Bookmark b = new Bookmark();
		b.setId(doc.getId());
		b.setUserId(doc.getUserId());
		b.setRelationId(doc.getRelationId());
		b.setRelationType(doc.getRelationType());
		b.setPublicVisible(doc.isPublicVisible());
		b.setTitle(doc.getTitle());
		b.setDescription(doc.getDescription());
		return b;
	}

	private static Bookmark toEntity(Document d)
	{
		Bookmark b = new Bookmark();
		b.setId(d.getInteger("_id"));
		b.setUserId(d.getInteger("userId", 0));
		b.setRelationId(d.getInteger("relationId", 0));
		b.setRelationType(d.getInteger("relationType", 0));
		b.setPublicVisible(Boolean.TRUE.equals(d.getBoolean("publicVisible")));
		b.setTitle(d.getString("title"));
		b.setDescription(d.getString("description"));
		return b;
	}
}
