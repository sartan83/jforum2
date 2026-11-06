package net.jforum.document.repository;

import java.util.Optional;

import net.jforum.document.MailIntegrationDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MailIntegrationDocumentRepository extends MongoRepository<MailIntegrationDocument, Integer>
{
	Optional<MailIntegrationDocument> findFirstByForumId(int forumId);

	void deleteByForumId(int forumId);
}
