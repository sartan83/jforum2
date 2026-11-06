package net.jforum.document.repository;

import net.jforum.document.RoleDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoleDocumentRepository extends MongoRepository<RoleDocument, Integer>
{
}
