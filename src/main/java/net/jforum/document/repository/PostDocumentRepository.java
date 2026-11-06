package net.jforum.document.repository;

import net.jforum.document.PostDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PostDocumentRepository extends MongoRepository<PostDocument, Integer>
{
}
