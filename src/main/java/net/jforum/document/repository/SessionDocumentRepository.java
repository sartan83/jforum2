package net.jforum.document.repository;

import net.jforum.document.SessionDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SessionDocumentRepository extends MongoRepository<SessionDocument, Integer>
{
}
