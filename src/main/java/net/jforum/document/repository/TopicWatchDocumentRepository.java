package net.jforum.document.repository;

import net.jforum.document.TopicWatchDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicWatchDocumentRepository extends MongoRepository<TopicWatchDocument, String>
{
}
