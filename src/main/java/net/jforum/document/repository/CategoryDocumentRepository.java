package net.jforum.document.repository;

import net.jforum.document.CategoryDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoryDocumentRepository extends MongoRepository<CategoryDocument, Integer>
{
}
