package net.jforum.document.repository;

import net.jforum.document.ConfigDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConfigDocumentRepository extends MongoRepository<ConfigDocument, Integer>
{
}
