package net.jforum.document.repository;

import net.jforum.document.TopicDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicDocumentRepository extends MongoRepository<TopicDocument, Integer>
{
}
