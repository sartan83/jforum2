package net.jforum.document.repository;

import java.util.Optional;

import net.jforum.document.AttachQuotaDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AttachQuotaDocumentRepository extends MongoRepository<AttachQuotaDocument, Integer>
{
	Optional<AttachQuotaDocument> findFirstByGroupIdOrderByIdAsc(int groupId);
}
