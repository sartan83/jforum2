package net.jforum.document.repository;

import net.jforum.document.PollDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PollDocumentRepository extends MongoRepository<PollDocument, Integer>
{
}
