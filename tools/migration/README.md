# JForum SQL Server → MongoDB migration

One-shot Spring Boot command line tool (`net.jforum.migration.MigrationApplication`) that copies a legacy
JForum 2.1.8 database on Microsoft SQL Server (schema `WEB-INF/config/database/sqlserver/sqlserver_db_struct.sql`)
into the MongoDB 8 database used by `jforum-boot`.

* Reads the legacy tables with JDBC (`com.microsoft.sqlserver:mssql-jdbc`), forward-only, `migration.batch-size` rows per fetch.
* Writes with `MongoTemplate` unordered bulk `replaceOne(_id, upsert)` → **re-runs are idempotent** (no duplicates).
* `_id` = legacy numeric id; blank ids and the legacy "none" value `0` become `null`.
* Copies attachment files from the legacy upload directory into GridFS.
* Raises `counters.<collection>.seq` to `max(_id)` so the application's `SequenceService` continues after the imported ids.
* Logs every table with its row/document counts and ends with a **verification** comparing SQL row counts with MongoDB.

## Domain model: shared, not duplicated

The documents are built with the **application's own domain classes** (`jforum-boot/src/main/java/net/jforum/domain`),
so collection names, field names, enum encodings, `_class` and indexes are exactly what the application reads and cannot
drift. `jforum-boot` is a Spring Boot fat jar (not usable as a dependency), and the domain package only depends on
Spring Data MongoDB annotations, so this module adds that source folder with `build-helper-maven-plugin` and compiles
only `net/jforum/domain/**` (see `pom.xml`). No mapping layer is duplicated; the SQL → object mapping lives in
`SqlServerToMongoMigration`, mirroring the legacy `Generic*DAO` row mappers.

## Prerequisites

* Java 21, Docker (only for the tests / `docker run`).
* Network access from the tool to SQL Server (read-only account is enough) and to MongoDB 8.
* A copy of the legacy upload directory (legacy `attachments.store.dir`, by default `<webapp>/upload`) if attachments
  must be migrated with their content.

## Build

```bash
./mvnw -pl tools/migration -am package            # runs the Testcontainers tests (SQL Server 2022 + MongoDB 8)
./mvnw -pl tools/migration -am package -DskipTests
# -> tools/migration/target/jforum-migration-3.0.0-SNAPSHOT.jar
```

## Run

Stop the application (or put it in maintenance) while migrating, then:

```bash
java -jar tools/migration/target/jforum-migration-3.0.0-SNAPSHOT.jar \
  --spring.datasource.url='jdbc:sqlserver://sqlhost:1433;databaseName=jforum;encrypt=true;trustServerCertificate=true' \
  --spring.datasource.username=jforum_ro \
  --spring.datasource.password='***' \
  --spring.data.mongodb.uri='mongodb://mongohost:27017/jforum?replicaSet=rs0' \
  --migration.upload-dir=/srv/jforum/upload \
  --migration.drop-target=true
```

Dry run first (reads everything, writes nothing, prints the counts report):

```bash
java -jar tools/migration/target/jforum-migration-3.0.0-SNAPSHOT.jar --migration.dry-run=true \
  --spring.datasource.url=... --spring.datasource.username=... --spring.datasource.password=...
```

Via Docker (no image to build, the jar is mounted into a JRE container):

```bash
docker run --rm --network host \
  -v "$PWD/tools/migration/target:/app" -v /srv/jforum/upload:/upload:ro \
  eclipse-temurin:21-jre java -jar /app/jforum-migration-3.0.0-SNAPSHOT.jar \
  --spring.datasource.url='jdbc:sqlserver://localhost:1433;databaseName=jforum;encrypt=false' \
  --spring.datasource.username=sa --spring.datasource.password='***' \
  --spring.data.mongodb.uri='mongodb://localhost:27017/jforum' \
  --migration.upload-dir=/upload
```

With the root `docker compose up` stack, use `--network jforum2_default` and `mongodb://mongo:27017/jforum` instead
of `--network host` (check the network name with `docker network ls`).

Exit status: `0` when the migration completed and the verification passed (or after a dry run), `1` on error or when the
verification found a mismatch (`migration.fail-on-mismatch=true`).

## Options

| Option | Default | Description |
|---|---|---|
| `--spring.datasource.url` | – (required) | JDBC url of the legacy SQL Server database |
| `--spring.datasource.username` / `--spring.datasource.password` | – | SQL Server credentials (read access only) |
| `--spring.data.mongodb.uri` | `mongodb://localhost:27017/jforum` | Target database (same value as the application's) |
| `--migration.drop-target` | `false` | Drop the target collections, `counters` and the GridFS bucket before importing. With `false` documents are upserted by id (documents created by the application with other ids are kept) |
| `--migration.batch-size` | `500` | Documents per MongoDB bulk write and JDBC fetch size |
| `--migration.upload-dir` | – | Legacy attachments directory; `physical_filename` is resolved against it. Without it only the metadata is migrated (`gridFsId` = null) |
| `--migration.dry-run` | `false` | Count only, write nothing |
| `--migration.gridfs-bucket` | `fs` | GridFS bucket for the attachment bytes |
| `--migration.source-timezone` | `UTC` | Zone of the legacy `DATETIME` values (stored without zone by SQL Server) |
| `--migration.fail-on-mismatch` | `true` | Exit with status 1 when the verification fails |

## Mapping

All documents carry the `_class` of the domain class. Flags are legacy integers mapped like the legacy DAOs
(`rs.getInt(col) == 1`, so `NULL` is `false`); enums use the domain's `fromLegacy(int)`.

| SQL Server | MongoDB collection | Notes |
|---|---|---|
| `jforum_users` + `jforum_user_groups` | `users` | `groupIds` from `jforum_user_groups` (distinct, sorted); password hash kept as is (legacy MD5, upgraded to bcrypt by the app at first login); `active` = `user_active = 1`, `NULL` = active unless an activation key is pending; `karmaVotes` = karma rows received; `avatarType` = `AvatarType.fromLegacy` |
| `jforum_groups` + `jforum_roles` + `jforum_role_values` | `groups` | `roles: [{name, values[]}]` embedded; only group roles (`group_id > 0`); values distinct |
| `jforum_categories` | `categories` | `display_order` → `order` |
| `jforum_forums` | `forums` | `totalPosts` = posts in the forum; `lastPostInfo` from `forum_last_post_id` (post, user, topic replies) |
| `jforum_topics` + `jforum_vote_desc` / `jforum_vote_results` / `jforum_vote_voters` | `topics` | `poll` embedded (`{_id: vote_id, label, startTime, length, options[{_id, text, voteCount}], voters[{userId, ip}]}`); `lastPostTime/UserId/Username` from `topic_last_post_id`; `hasAttach` if a post has attachments; status/type via `fromLegacy` |
| `jforum_posts` + `jforum_posts_text` | `posts` | `subject`/`text` from `jforum_posts_text`; `karma` = SUM/COUNT of karma points (legacy integer average) |
| `jforum_privmsgs` + `jforum_privmsgs_text` | `private_messages` | `type` = `PrivateMessageType.fromLegacy`; `hasAttachments` if an attachment references it |
| `jforum_attach` + `jforum_attach_desc` + files | `attachments` + GridFS (`fs.files`/`fs.chunks`) | file `<upload-dir>/<physical_filename>` stored in GridFS (`metadata.attachmentId` = legacy id), its ObjectId hex in `gridFsId`; missing files are logged and counted, `gridFsId` stays null |
| `jforum_karma` | `karma` | |
| `jforum_bookmarks` | `bookmarks` | `relationType` = `BookmarkType.fromLegacy` |
| `jforum_topics_watch` | `topic_watch` | `_id` = `"<topicId>:<userId>"`, `read` = `is_read` |
| `jforum_forums_watch` | `forum_watch` | `_id` = `"<forumId>:<userId>"` |
| `jforum_ranks` | `rankings` | |
| `jforum_smilies` | `smilies` | `url` = `/images/smilies/<disk_name>` (as the app builds it) |
| `jforum_banlist` | `banlist` | |
| `jforum_config` | `config` | `_id` = `config_name` |
| `jforum_moderation_log` | `moderation_log` | `username`/`postUsername` resolved from `jforum_users` |
| `jforum_extension_groups` | `extension_groups` | |
| `jforum_extensions` | `extensions` | |
| `jforum_quota_limit` + `jforum_attach_quota` | `quota_limits` | `groupIds` from `jforum_attach_quota` |
| `jforum_banner` | `banners` | optional table |
| `jforum_api` | `api_keys` | optional table |
| – | `counters` | `seq` = max(`_id`) for every collection above with numeric ids, plus `polls` (max `vote_id`); never lowered |

Not migrated (runtime/derived data, rebuilt by the application): `jforum_sessions`, `jforum_search_*`, `jforum_words`,
`jforum_themes`, user-level roles (`jforum_roles.user_id`). Tables missing in the source (e.g. `jforum_banner` and
`jforum_api` are not created by the shipped SQL Server script, nor is a valid `jforum_moderation_log`: its DDL is
malformed) are reported as skipped.

## Verification

After a real run the tool compares, per table, the number of distinct source rows with the documents (or embedded
elements: `users.groupIds`, `groups.roles`, `groups.roles.values`, `topics.poll.options|voters`,
`quota_limits.groupIds`, posts/PM texts, attachment files in GridFS) and prints:

```
verify jforum_posts -> posts: 1234 = 1234 OK
...
verification: 38 checks, 0 mismatch(es) -> OK
```

The collection checks count all documents, so run with `--migration.drop-target=true` on a target that contains
application data you don't want to keep, otherwise extra documents show up as mismatches. Spot checks after the run:
log in with an existing account (legacy password), open a forum/topic list, a topic with a poll, a PM and download an
attachment.

## Rollback

The source database is only read. To roll back, drop the target database (`mongosh jforum --eval 'db.dropDatabase()'`)
or re-run with `--migration.drop-target=true`; to keep the previous MongoDB state, take a `mongodump` before migrating
and `mongorestore --drop` it. The legacy application can keep running on SQL Server until the switch.

## Tests

`MigrationIntegrationTest` (JUnit 5 + Testcontainers, part of the root `./mvnw -B verify`) starts
`mcr.microsoft.com/mssql/server:2022-latest` and MongoDB 8, creates the schema from `sqlserver_db_struct.sql` (UTF-16,
split on `GO`; the malformed moderation log DDL is replaced by `src/test/resources/legacy/moderation-log.sql`), adds
`jforum_banner`/`jforum_api`, loads `sqlserver_data_dump.sql` plus `extra-data.sql` (users, roles with values, PMs,
polls, attachments, karma, watches, ...) and asserts documents, relationships, ids, GridFS content, counters,
idempotent re-runs, dry run and mismatch detection. Images can be overridden with `-Dmongodb.image=...` / `-Dmssql.image=...`.
