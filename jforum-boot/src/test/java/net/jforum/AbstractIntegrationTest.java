package net.jforum;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import net.jforum.bootstrap.BootstrapDataLoader;
import net.jforum.config.ForumConfig;
import net.jforum.format.SmilieService;
import net.jforum.security.PermissionService;

/**
 * Base class for integration tests: real MongoDB 8 (Testcontainers), MockMvc, and a fresh database
 * re-bootstrapped (legacy data dump equivalent: Admin/admin, Test Forum, Welcome topic) before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MongoTestContainer.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MongoTemplate mongo;

    @Autowired
    private BootstrapDataLoader bootstrap;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private SmilieService smilieService;

    @Autowired
    private ForumConfig forumConfig;

    @BeforeEach
    void resetDatabase() throws Exception {
        for (String name : mongo.getCollectionNames()) {
            if (!name.startsWith("system.")) {
                mongo.getCollection(name).deleteMany(new org.bson.Document());
            }
        }
        permissionService.invalidate();
        smilieService.invalidate();
        forumConfig.reload();
        ApplicationArguments args = new DefaultApplicationArguments();
        bootstrap.run(args);
    }
}
