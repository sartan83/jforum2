# jforum2
JForum 2.x series, modernized: **Java 21 + Spring Boot 3.5 + Spring Data MongoDB (MongoDB 8.x / Atlas)**.

Functionality, URLs (`*.page`, `config/urlPattern.properties`, `config/modulesMapping.properties`) and UI (the original
FreeMarker templates in `templates/`) are those of JForum 2.1.8. The JDBC layer, connection pools, SQL scripts,
JBoss Cache, Quartz and Lucene were replaced by MongoDB, the driver connection pool, Spring `@Scheduled` jobs and
MongoDB text search.

## Requirements
- Java 21 and Maven 3.9+ (only to build/run outside Docker)
- Docker with Compose v2 (local stack)
- MongoDB 8.x: the bundled `mongo:8.0` container or a MongoDB Atlas cluster (8.x series)

## Run locally with docker compose
```bash
docker compose up --build                            # MongoDB 8.0 + JForum on http://localhost:8080
SPRING_PROFILES_ACTIVE=demo docker compose up --build # same, with demo categories/forums/users/topics/posts
docker compose --profile tools up -d mongo-express   # optional data browser on http://localhost:8081
docker compose down                                  # stop (data kept in the mongo-data volume)
```
If Maven Central rate-limits the build host, pass a mirror: `MAVEN_MIRROR_URL=https://maven-central-eu.storage-download.googleapis.com/maven2 docker compose build`.

Note: the `mongo:8.0` image does not start on Linux kernels >= 6.19; there use `mongo:8.2` or Atlas.

## Run from the command line
```bash
mvn verify                     # build + tests (Testcontainers MongoDB; -Dmongodb.image=mongo:8.2 to override)
java -jar target/jforum-2.1.8-boot.jar --spring.data.mongodb.uri=mongodb://localhost:27017/jforum
```

## Configuration
Everything lives in `src/main/resources/application.yml` and can be overridden with environment variables,
`--key=value` arguments or a Spring profile.

| Setting | Meaning |
| --- | --- |
| `SPRING_DATA_MONGODB_URI` / `spring.data.mongodb.uri` | MongoDB connection string. Default `mongodb://localhost:27017/jforum` |
| `JFORUM_HOME` / `jforum.home` | Writable directory with the runtime copy of config, templates, images, avatars and tmp files |
| `jforum."<key>"` | The functional keys of the former `SystemGlobals.properties`, same names (e.g. `jforum."topics.per.page"`); env vars use relaxed binding (`JFORUM_FORUM_LINK` → `forum.link`) |

Values changed from the admin *Configurations* panel are stored in MongoDB and shared by all instances.

### MongoDB Atlas
Create a cluster (8.x series), a database user and an IP access list entry, then point the app at the SRV URI:
```bash
export SPRING_DATA_MONGODB_URI='mongodb+srv://<user>:<password>@<cluster>.mongodb.net/jforum?retryWrites=true&w=majority'
docker compose up --build app   # or java -jar ...
```
Collections and indexes are created automatically at startup (`spring.data.mongodb.auto-index-creation`).

### Backup, replication, monitoring
They are delegated to the database platform and no longer part of the application: MongoDB Atlas provides
continuous/snapshot backups with point-in-time restore, replica sets with automatic failover, and
monitoring/alerting (Real-Time Performance Panel, Performance Advisor, alerts). The legacy SQL backup/maintenance
scripts, the custom connection pools, the JBoss Cache cluster configuration and the database Quartz jobs were removed.
The application exposes Spring Boot Actuator `GET /actuator/health` (includes the MongoDB ping) for load balancers.

## Migrating data from SQL Server
See [`tools/migration/README.md`](tools/migration/README.md): a Spring Boot CLI that reads every `jforum_*` table from
SQL Server, writes the MongoDB documents preserving ids and relations, and prints a row/document parity report.

## Project layout
- `src/main/java/net/jforum` – original JForum code (actions, entities, repositories, BBCode, security) running
  inside Spring Boot; `boot/` bootstrap, `persistence/` store interfaces, `persistence/mongo/` MongoDB implementations,
  `document/` MongoDB documents
- `templates/`, `images/` – original UI, packaged as classpath resources
- `tests/core/java` – JUnit 5 + Spring Boot Test + Testcontainers tests
- `tools/migration` – SQL Server → MongoDB migration tool
- `docs/verification` – verification report of the running application

## History
JForum was a bulletin board software based on PHPBB written by Rafael Steil in the 2000's, used by sites like
JavaRanch.com and GUJ.com.br. The actively maintained version is at [jforum.net](http://jforum.net).
