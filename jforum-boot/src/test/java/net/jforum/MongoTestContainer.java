package net.jforum;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared MongoDB 8 container (single-node replica set) wired through @ServiceConnection. */
@TestConfiguration(proxyBeanMethods = false)
public class MongoTestContainer {

    static final MongoDBContainer MONGO = new MongoDBContainer(
        DockerImageName.parse(System.getProperty("mongodb.image", "mongo:8")).asCompatibleSubstituteFor("mongo"));

    static {
        MONGO.start();
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoDBContainer() {
        return MONGO;
    }
}
