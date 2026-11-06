package net.jforum.document.repository;

import net.jforum.document.BanlistDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface BanlistDocumentRepository extends MongoRepository<BanlistDocument, Integer>
{
}
