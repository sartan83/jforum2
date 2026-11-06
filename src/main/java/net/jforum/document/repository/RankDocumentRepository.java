package net.jforum.document.repository;

import net.jforum.document.RankDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RankDocumentRepository extends MongoRepository<RankDocument, Integer>
{
}
