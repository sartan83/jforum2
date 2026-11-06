# JForum SQL Server → MongoDB migration tool

A standalone Spring Boot 3.5 / Java 21 command line tool that copies a legacy JForum 2.1.8 SQL Server database
into the MongoDB document model of the modernized application. It works the same way against a local MongoDB
and MongoDB Atlas.

- It reads **every** `jforum_*` base table of the source schema (read-only JDBC, streamed with `fetchSize = batch size`).
- The legacy primary key becomes `_id`. No id is generated for tables that have a primary key, so every
  relation (`topicId`, `forumId`, `userId`, ...) still points at the right document.
- It initializes the `sequences` collection (`{_id: <collection>, value: max(_id)}`) that
  `net.jforum.persistence.mongo.SequenceGenerator` uses.
- It writes a per-table **parity report** (`parity-report.json` + `parity-report.txt`) and exits with code `2`
  on any mismatch.

The legacy SQL Server schema and seed data used by the tests are in `src/main/resources/sqlserver/`.

## Build

```bash
cd tools/migration
mvn verify                    # builds target/jforum-migration.jar and runs the Testcontainers tests
mvn -DskipTests package       # jar only
```

The tests need Docker. They start `mcr.microsoft.com/mssql/server:2022-latest` (with the EULA accepted) and
`mongo:8.0`. Override the images with `-Dmssql.image=...` and `-Dmongodb.image=...`. Use
`-Dmongodb.image=mongo:8.2` on hosts with Linux kernel 6.19 or newer, where mongo 8.0 does not start.

## Usage

```bash
java -jar target/jforum-migration.jar \
  --source.url='jdbc:sqlserver://dbhost:1433;databaseName=jforum;encrypt=true;trustServerCertificate=true' \
  --source.user=jforum --source.password=secret \
  --target.uri='mongodb+srv://user:pass@cluster0.example.mongodb.net/jforum?retryWrites=true&w=majority' \
  --drop-target \
  --attachments-dir=/var/jforum/upload
```

| Option | Default | Description |
|---|---|---|
| `--source.url` | (required) | SQL Server JDBC URL: `jdbc:sqlserver://host:1433;databaseName=...;encrypt=...` |
| `--source.user` / `--source.username`, `--source.password` | — | SQL Server credentials (you can also put them in the JDBC URL) |
| `--source.schema` | `dbo` | Schema that contains the `jforum_*` tables |
| `--source.timezone` | JVM timezone | Timezone used to read `datetime` values, which have no timezone. They are stored as UTC BSON dates. |
| `--target.uri` | (required unless `--dry-run`) | `mongodb://...` or `mongodb+srv://...` |
| `--target.database` | database of the URI, else `jforum` | Target database |
| `--drop-target` | off | Drops the target collections (and the GridFS bucket `fs` when `--attachments-dir` is set) before the import, and sets each sequence to exactly `max(_id)` |
| `--batch-size` | `1000` | JDBC fetch size and number of documents per MongoDB bulk write |
| `--dry-run` | off | Reads and transforms everything, writes nothing, and reports what would be written |
| `--attachments-dir` | — | JForum upload directory (`attachments.upload.dir`). When set, files are copied to GridFS. |
| `--report-dir` | `./migration-report` | Directory for `parity-report.json` and `parity-report.txt` |

You can also pass every option as an environment variable, using Spring relaxed binding (`SOURCE_URL`,
`SOURCE_PASSWORD`, `TARGET_URI`, `DROP_TARGET=true`, ...). This keeps passwords out of the shell history.
Passwords are redacted in the logs and in the report.

Without `--drop-target` the import is an idempotent upsert by `_id`: a second run replaces the documents.
Sequences are only raised with `$max`, never lowered, so ids already allocated by the new application are not
reused. Documents that exist only in MongoDB are not deleted, and they show up as a parity mismatch.

Exit codes: `0` means the parity is OK. `2` means a parity or sequence mismatch (see the report). `1` means
any other error (connection, SQL, ...).

## Mapping

Field names are the camelCase of the column names (`topic_first_post_id` → `topicFirstPostId`; the stray
trailing space of `jforum_attach_desc.[description ]` is trimmed). Types are converted as follows:

- integer types → int32 (bigint → int64 only when the value does not fit)
- decimal/float → double
- `datetime` → date
- `char(n)` → right-trimmed string
- `text`/`varchar` → string
- binary → BinData

A SQL `NULL` leaves the field out of the document.

| Source table(s) | Collection | `_id` | Notes |
|---|---|---|---|
| `jforum_posts` + `jforum_posts_text` | `posts` | `post_id` | `post_subject` → `subject`, `post_text` → `text` (LEFT JOIN: a post without a text row has no `subject`/`text`) |
| `jforum_topics` | `topics` | `topic_id` | as is |
| `jforum_forums` | `forums` | `forum_id` | as is |
| `jforum_categories` | `categories` | `categories_id` | as is |
| `jforum_users` + `jforum_user_groups` | `users` | `user_id` | `groupIds: [int]`, sorted and distinct; `usernameLower` = `username` lower-cased (`Locale.ROOT`), used by `MongoUserStore` lookups |
| `jforum_roles` + `jforum_role_values` | `roles` | `role_id` | `roleValues: [String]` ordered by `role_value` (`RoleDocument`) |
| `jforum_privmsgs` + `jforum_privmsgs_text` | `privmsgs` | `privmsgs_id` | fields without the `privmsgs_` prefix as in `PrivateMessageDocument`: `type`, `subject`, `fromUserId`, `toUserId`, `date`, `ip`, `enableBbcode`, `enableHtml`, `enableSmilies`, `attachSig` (booleans), `text` (from `privmsgs_text`) |
| `jforum_vote_desc` + `jforum_vote_results` + `jforum_vote_voters` | `polls` | `vote_id` | `options: [{voteOptionId, voteOptionText, voteResult}]` ordered by option id, `voters: [{voteUserId, voteUserIp}]` |
| `jforum_attach` + `jforum_attach_desc` | `attachments` | `attach_id` | `attach_desc` columns flattened into the same document (`attachDescId`, `physicalFilename`, `realFilename`, `description`, `mimetype`, `filesize`, `uploadTime`, `thumb`, `extensionId`, ...) |
| files in `--attachments-dir` | GridFS bucket `fs` | ObjectId | `<dir>/<physical_filename>` and `<physical_filename>_thumb` if present. Filename = `real_filename`. `metadata: {attachId, physicalFilename, realFilename, mimetype, thumb}` |
| `jforum_groups` | `groups` | `group_id` | |
| `jforum_ranks` | `ranks` | `rank_id` | |
| `jforum_smilies` | `smilies` | `smilie_id` | |
| `jforum_themes` | `themes` | `themes_id` | |
| `jforum_words` | `words` | `word_id` | |
| `jforum_karma` | `karma` | `karma_id` | |
| `jforum_bookmarks` | `bookmarks` | `bookmark_id` | `publicVisible` as boolean (`BookmarkDocument`) |
| `jforum_config` | `config` | `config_id` | Legacy `ConfigStore` data (e.g. most users ever online). Not the `settings` collection, which holds the SystemGlobals overrides saved from the admin panel (`_id` = property key). |
| `jforum_quota_limit` | `quota_limit` | `quota_limit_id` | |
| `jforum_extension_groups` | `extension_groups` | `extension_group_id` | |
| `jforum_extensions` | `extensions` | `extension_id` | |
| `jforum_attach_quota` | `attach_quota` | `attach_quota_id` | |
| `jforum_banlist` | `banlist` | `banlist_id` | |
| `jforum_search_words` | `search_words` | `word_id` | |
| `jforum_moderation_log` | `moderation_log` | `log_id` | |
| `jforum_banner` (if present) | `banner` | `banner_id` | fields without the `banner_` prefix as in `BannerDocument` (`name`, `placement`, `description`, `clicks`, `views`, `url`, `weight`, `active` as boolean, `comment`, `type`, `width`, `height`) |
| `jforum_api` (if present) | `api` | `api_id` | |
| `jforum_sessions` | `sessions` | `session_user_id` | One session per user, as `SessionDocument` (`_id` = user id) |
| `jforum_topics_watch`, `jforum_forums_watch`, `jforum_search_results`, `jforum_search_topics`, `jforum_search_wordmatch` | same name without `jforum_` | generated `1..n` | Link/cache tables without a single-column key. Every column is kept as a field. The SQL Server DDL declares `jforum_forums_watch.forum_id` as an IDENTITY primary key, which is a schema bug, so that column is kept as a plain `forumId` field. |
| any other `jforum_*` table | name without `jforum_` | its single-column integer PK, else generated `1..n` | |

A `sequences` document is written for every target collection (`value` = highest numeric `_id`, `0` for an
empty collection).

Tables that the tool knows but cannot find in the source are listed under "Tables not found in the source" in
the report. They are not counted as a mismatch. For example, `jforum_banner` and `jforum_api` are not created by
the shipped `sqlserver_db_struct.sql`.

## Verifying the parity report

Each row compares the `COUNT(*)` of a source table with what is in MongoDB after the import:

| Source | Measure in MongoDB |
|---|---|
| main/single tables | number of documents in the collection |
| `jforum_posts_text`, `jforum_privmsgs_text`, `jforum_attach_desc` | number of documents with `subject` / `text` / `attachDescId` |
| `jforum_user_groups` | total size of `users.groupIds` (compared with the *distinct* `(user_id, group_id)` pairs; collapsed duplicates are listed as a warning) |
| `jforum_role_values` | total size of `roles.roleValues` |
| `jforum_vote_results` / `jforum_vote_voters` | total size of `polls.options` / `polls.voters` |
| files in `--attachments-dir` | number of `fs.files` with `metadata.attachId`, compared with the files found and uploaded (thumbnails included). Missing files are listed as a warning. |
| sequences | `sequences.value >= max(_id)` for every collection |

Example (`parity-report.txt`):

```
       SOURCE                   ROWS  COLLECTION   MEASURE                        TARGET
OK     jforum_users                5  users        documents                           5
OK     jforum_user_groups          6  users        embedded groupIds[]                 6
OK     jforum_posts                3  posts        documents                           3
OK     jforum_posts_text           3  posts        documents with subject              3
DIFF   jforum_topics               2  topics       documents                           3
...
STATUS: MISMATCH
```

`parity-report.json` contains the same data (`status`, `tables[]`, `sequences[]`, `missingTables[]`,
`warnings[]`) for automated checks, e.g. `jq -e '.status == "OK"' migration-report/parity-report.json`.
Child rows whose parent does not exist in the source (for example a `jforum_role_values` row of a deleted
role) cannot be embedded, so they appear as a `DIFF` on the embedded measure. Clean them up in the source, or
accept the difference after reviewing it.
