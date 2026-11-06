package net.jforum.document.repository;

import net.jforum.document.ForumWatchDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ForumWatchDocumentRepository extends MongoRepository<ForumWatchDocument, String>
{
}
