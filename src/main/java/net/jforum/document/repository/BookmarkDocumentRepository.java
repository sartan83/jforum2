package net.jforum.document.repository;

import java.util.List;

import net.jforum.document.BookmarkDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookmarkDocumentRepository extends MongoRepository<BookmarkDocument, Integer>
{
	List<BookmarkDocument> findByUserIdOrderByTitleAsc(int userId);

	BookmarkDocument findFirstByRelationIdAndRelationTypeAndUserId(int relationId, int relationType, int userId);
}
