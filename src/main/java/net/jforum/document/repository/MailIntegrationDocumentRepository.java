package net.jforum.document.repository;

import net.jforum.document.MailIntegrationDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MailIntegrationDocumentRepository extends MongoRepository<MailIntegrationDocument, Integer>
{
}
