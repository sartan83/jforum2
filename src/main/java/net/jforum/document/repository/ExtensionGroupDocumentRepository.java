package net.jforum.document.repository;

import net.jforum.document.ExtensionGroupDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExtensionGroupDocumentRepository extends MongoRepository<ExtensionGroupDocument, Integer>
{
}
