package net.jforum.document.repository;

import net.jforum.document.SmilieDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SmilieDocumentRepository extends MongoRepository<SmilieDocument, Integer>
{
}
