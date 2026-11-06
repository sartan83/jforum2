package net.jforum.document;

import net.jforum.document.repository.SmilieDocumentRepository;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * The application class lives in <code>net.jforum.boot</code>, so Spring Boot's auto-configured
 * repository scan does not reach <code>net.jforum.document.repository</code>: enable it explicitly
 * for the whole package (shared by every area).
 */
@Configuration(proxyBeanMethods = false)
@EnableMongoRepositories(basePackageClasses = SmilieDocumentRepository.class)
public class MongoRepositoriesConfig
{
}
