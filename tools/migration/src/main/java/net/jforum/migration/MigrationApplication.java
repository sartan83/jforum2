package net.jforum.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * One-shot JForum 2.1.8 SQL Server to MongoDB migration. See {@code tools/migration/README.md}.
 * The process exits with status 0 when the migration (or dry run) completed and verified, 1 otherwise.
 */
@SpringBootApplication
@EnableConfigurationProperties(MigrationProperties.class)
public class MigrationApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(MigrationApplication.class, args)));
    }
}
