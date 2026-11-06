package net.jforum.document.repository;

import net.jforum.document.ModerationLogDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ModerationLogDocumentRepository extends MongoRepository<ModerationLogDocument, Integer>
{
}
