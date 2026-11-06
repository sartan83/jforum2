package net.jforum.document.repository;

import net.jforum.document.PrivateMessageDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PrivateMessageDocumentRepository extends MongoRepository<PrivateMessageDocument, Integer>
{
}
