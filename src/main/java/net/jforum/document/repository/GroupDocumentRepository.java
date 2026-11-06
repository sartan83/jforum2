package net.jforum.document.repository;

import net.jforum.document.GroupDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GroupDocumentRepository extends MongoRepository<GroupDocument, Integer>
{
}
