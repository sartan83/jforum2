package net.jforum.document.repository;

import java.util.List;

import net.jforum.document.KarmaDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface KarmaDocumentRepository extends MongoRepository<KarmaDocument, Integer>
{
	boolean existsByPostIdAndFromUserId(int postId, int fromUserId);

	List<KarmaDocument> findByTopicIdAndFromUserId(int topicId, int fromUserId);

	long countByFromUserId(int fromUserId);
}
