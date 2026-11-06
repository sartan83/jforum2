package net.jforum.document.repository;

import net.jforum.document.ExtensionDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExtensionDocumentRepository extends MongoRepository<ExtensionDocument, Integer>
{
}
