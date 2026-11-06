package net.jforum.document.repository;

import net.jforum.document.QuotaLimitDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface QuotaLimitDocumentRepository extends MongoRepository<QuotaLimitDocument, Integer>
{
}
