package net.jforum.document.repository;

import net.jforum.document.ApiDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ApiDocumentRepository extends MongoRepository<ApiDocument, Integer>
{
}
