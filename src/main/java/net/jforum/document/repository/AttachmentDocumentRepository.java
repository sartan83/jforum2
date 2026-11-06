package net.jforum.document.repository;

import java.util.List;

import net.jforum.document.AttachmentDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AttachmentDocumentRepository extends MongoRepository<AttachmentDocument, Integer>
{
	List<AttachmentDocument> findByPostIdOrderByIdAsc(int postId);

	long countByPostId(int postId);
}
