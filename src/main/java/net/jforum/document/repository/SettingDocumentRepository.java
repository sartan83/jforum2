package net.jforum.document.repository;

import net.jforum.document.SettingDocument;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SettingDocumentRepository extends MongoRepository<SettingDocument, String>
{
}
