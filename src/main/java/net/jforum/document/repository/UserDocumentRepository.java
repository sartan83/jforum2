package net.jforum.document.repository;

import net.jforum.document.UserDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserDocumentRepository extends MongoRepository<UserDocument, Integer>
{
}
