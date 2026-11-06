package net.jforum.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests needing a real MongoDB: one container per JVM, wired into
 * <code>spring.data.mongodb.*</code> through {@link ServiceConnection}.
 * The image defaults to <code>mongo:8.0</code> and can be overridden with -Dmongodb.image=mongo:8.2
 * (mongo 8.0 does not start on Linux kernels &gt;= 6.19).
 */
@Testcontainers
public abstract class MongoTestSupport
{
	@ServiceConnection
	protected static final MongoDBContainer MONGO = new MongoDBContainer(
		DockerImageName.parse(System.getProperty("mongodb.image", "mongo:8.0")).asCompatibleSubstituteFor("mongo"));

	static {
		MONGO.start();
	}
}
