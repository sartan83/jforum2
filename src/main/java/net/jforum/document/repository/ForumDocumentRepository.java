package net.jforum.document.repository;

import net.jforum.document.ForumDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ForumDocumentRepository extends MongoRepository<ForumDocument, Integer>
{
}
