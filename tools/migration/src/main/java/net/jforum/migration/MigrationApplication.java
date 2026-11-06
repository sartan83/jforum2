package net.jforum.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

/**
 * Command line entry point of the JForum SQL Server to MongoDB migration.
 * The MongoDB client is built from <code>--target.uri</code> by {@link Migration}, so Spring Boot's
 * Mongo auto-configuration (which would connect to localhost) is disabled.
 */
@SpringBootApplication(exclude = {
	MongoAutoConfiguration.class,
	MongoDataAutoConfiguration.class,
	MongoRepositoriesAutoConfiguration.class })
public class MigrationApplication
{
	public static void main(String[] args)
	{
		System.exit(SpringApplication.exit(SpringApplication.run(MigrationApplication.class, args)));
	}
}
