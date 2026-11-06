package net.jforum.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

import net.jforum.migration.report.ParityEntry;
import net.jforum.migration.report.ParityReport;
import net.jforum.migration.report.SequenceEntry;

/**
 * Loads the shipped SQL Server schema + data dump (+ extra fixture rows) into SQL Server 2022, migrates it
 * into MongoDB and checks the parity report and the transformed documents.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationIntegrationTest
{
	static final MongoDBContainer MONGO = new MongoDBContainer(
		DockerImageName.parse(System.getProperty("mongodb.image", "mongo:8.0")).asCompatibleSubstituteFor("mongo"));

	@TempDir
	static Path work;

	static Path attachments;
	static MongoClient client;
	static MongoDatabase db;
	static ParityReport report;

	@BeforeAll
	static void setUp() throws Exception
	{
		LegacySqlServer.start();
		MONGO.start();
		client = MongoClients.create(MONGO.getConnectionString());
		db = client.getDatabase("jforum");

		attachments = Files.createDirectories(work.resolve("upload"));
		Files.createDirectories(attachments.resolve("2024/3"));
		Files.writeString(attachments.resolve("2024/3/1_abc123.png"), "png-content");
		Files.writeString(attachments.resolve("2024/3/1_abc123.png_thumb"), "thumb");
		Files.writeString(attachments.resolve("2024/3/5_def456.txt"), "hello notes");

		report = new Migration(options("jforum", true, false, 2, attachments, work.resolve("report"))).run();
	}

	static MigrationOptions options(String database, boolean drop, boolean dryRun, int batchSize, Path attachmentsDir, Path reportDir)
	{
		return new MigrationOptions(LegacySqlServer.jdbcUrl(), LegacySqlServer.CONTAINER.getUsername(),
			LegacySqlServer.CONTAINER.getPassword(), "dbo", TimeZone.getTimeZone("UTC"),
			MONGO.getConnectionString() + "/" + database, database, drop, dryRun, batchSize, attachmentsDir, reportDir);
	}

	static Document find(String collection, Object id)
	{
		return db.getCollection(collection).find(Filters.eq("_id", id)).first();
	}

	@Test
	@Order(1)
	void parityReportIsOkAndCoversEveryTable() throws Exception
	{
		assertThat(report.isOk()).as(report.toText()).isTrue();
		assertThat(report.missingTables).isEmpty();

		long sourceTables = LegacySqlServer.count(
			"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME LIKE 'jforum[_]%'");
		assertThat(report.tables.stream().map(ParityEntry::source).filter(s -> s.startsWith("jforum_")).distinct().count())
			.isEqualTo(sourceTables);

		assertThat(report.entry("jforum_posts", "documents").sourceRows()).isEqualTo(3);
		assertThat(report.entry("jforum_posts_text", "documents with subject").targetCount()).isEqualTo(3);
		assertThat(report.entry("jforum_privmsgs_text", "documents with text").targetCount()).isEqualTo(2);
		assertThat(report.entry("jforum_role_values", "embedded roleValues[]").targetCount())
			.isEqualTo(LegacySqlServer.count("SELECT COUNT(*) FROM jforum_role_values"));
		assertThat(report.entry("jforum_vote_results", "embedded options[]").targetCount()).isEqualTo(3);
		assertThat(report.entry("jforum_vote_voters", "embedded voters[]").targetCount()).isEqualTo(2);
		// 7 rows, one duplicated (user 3 in group 3)
		assertThat(report.entry("jforum_user_groups", "embedded groupIds[]").sourceRows()).isEqualTo(6);
		assertThat(report.warnings).anyMatch(w -> w.contains("duplicate"));
		// 1_abc123.png + its thumb + 5_def456.txt; 1_missing.png is reported
		assertThat(report.entry("files in " + attachments, "GridFS files").targetCount()).isEqualTo(3);
		assertThat(report.warnings).anyMatch(w -> w.contains("2024/3/1_missing.png"));

		JsonNode json = new ObjectMapper().readTree(work.resolve("report").resolve(Migration.JSON_REPORT).toFile());
		assertThat(json.get("status").asText()).isEqualTo("OK");
		assertThat(json.get("tables").size()).isEqualTo(report.tables.size());
		assertThat(Files.readString(work.resolve("report").resolve(Migration.TEXT_REPORT))).contains("STATUS: OK", "jforum_posts_text");
	}

	@Test
	@Order(2)
	void postsAreMergedWithTheirText()
	{
		Document post = find("posts", 12);
		assertThat(post.getInteger("topicId")).isEqualTo(10);
		assertThat(post.getInteger("userId")).isEqualTo(4);
		assertThat(post.getString("subject")).isEqualTo("Re: What do you prefer?");
		assertThat(post.getString("text")).isEqualTo("I voted <i>Java</i>.");
		assertThat(post.getDate("postTime")).isEqualTo(Date.from(Instant.parse("2024-03-02T18:45:10Z")));
		assertThat(post.getInteger("postEditCount")).isEqualTo(2);
		assertThat(post).doesNotContainKeys("postId", "postText", "postSubject");

		Document first = find("posts", 1);
		assertThat(first.getString("subject")).isNotBlank();
		assertThat(first.getString("text")).isNotBlank();
	}

	@Test
	@Order(3)
	void usersEmbedTheirGroups()
	{
		Document alice = find("users", 3);
		assertThat(alice.getString("username")).isEqualTo("alice");
		assertThat(find("users", 2).getString("usernameLower")).isEqualTo("admin");
		assertThat(alice.getList("groupIds", Integer.class)).containsExactly(1, 3);
		assertThat(alice.getString("userSig")).isEqualTo("Alice [b]sig[/b]");
		assertThat(alice.get("userKarma")).isEqualTo(4.5);
		assertThat(find("users", 2).getList("groupIds", Integer.class)).containsExactly(2);
		assertThat(find("users", 7).getList("groupIds", Integer.class)).containsExactly(1);
	}

	@Test
	@Order(4)
	void rolesEmbedTheirValues()
	{
		Document role = db.getCollection("roles").find(Filters.eq("name", "perm_moderation_forums")).first();
		assertThat(role.getInteger("groupId")).isEqualTo(3);
		assertThat(role.getList("roleValues", String.class)).containsExactly("1", "2");
		assertThat(role).doesNotContainKey("values");
		Document empty = db.getCollection("roles").find(Filters.eq("name", "perm_moderation")).first();
		assertThat(empty.getList("roleValues", String.class)).isEmpty();
	}

	@Test
	@Order(5)
	void pollsEmbedOptionsAndVoters()
	{
		Document poll = find("polls", 4);
		assertThat(poll.getInteger("topicId")).isEqualTo(10);
		assertThat(poll.getString("voteText")).isEqualTo("Favourite language?");
		List<Document> options = poll.getList("options", Document.class);
		assertThat(options).extracting(o -> o.getString("voteOptionText")).containsExactly("Java", "Kotlin", "Scala");
		assertThat(options).extracting(o -> o.getInteger("voteResult")).containsExactly(2, 0, 1);
		assertThat(poll.getList("voters", Document.class)).extracting(v -> v.getInteger("voteUserId")).containsExactly(3, 4);
	}

	@Test
	@Order(6)
	void privateMessagesAreMergedWithTheirText()
	{
		Document pm = find("privmsgs", 20);
		assertThat(pm.getString("subject")).isEqualTo("Hello Bob");
		assertThat(pm.getString("text")).isEqualTo("See the attached [i]file[/i].");
		assertThat(pm.getInteger("type")).isEqualTo(1);
		assertThat(pm.getInteger("fromUserId")).isEqualTo(3);
		assertThat(pm.getInteger("toUserId")).isEqualTo(4);
		assertThat(pm.getDate("date")).isEqualTo(Date.from(Instant.parse("2024-03-05T09:15:00Z")));
		assertThat(pm.getString("ip")).isEqualTo("10.0.0.1");
		assertThat(pm.getBoolean("enableBbcode")).isTrue();
		assertThat(pm.getBoolean("enableHtml")).isFalse();
		assertThat(pm.getBoolean("attachSig")).isFalse();
		assertThat(pm.keySet()).noneMatch(k -> k.startsWith("privmsgs"));
	}

	@Test
	@Order(7)
	void attachmentsKeepMetadataAndFilesGoToGridFs()
	{
		Document attach = find("attachments", 30);
		assertThat(attach).containsEntry("postId", 11).containsEntry("userId", 3).containsEntry("downloadCount", 5)
			.containsEntry("filesize", 12).containsEntry("thumb", 1).containsEntry("extensionId", 1)
			.containsEntry("description", "Architecture diagram").doesNotContainKey("privmsgsId");
		assertThat(find("attachments", 31)).containsEntry("privmsgsId", 20).doesNotContainKey("postId");
		assertThat(attach.getInteger("postId")).isEqualTo(11);
		assertThat(attach.getString("realFilename")).isEqualTo("diagram.png");
		assertThat(attach.getString("physicalFilename")).isEqualTo("2024/3/1_abc123.png");
		assertThat(attach.getString("description")).isEqualTo("Architecture diagram");
		assertThat(attach.getInteger("attachDescId")).isNotNull();
		assertThat(attach).doesNotContainKey("attachId");

		List<Document> files = db.getCollection("fs.files").find(Filters.eq("metadata.attachId", 30)).into(new java.util.ArrayList<>());
		assertThat(files).extracting(f -> f.get("metadata", Document.class).getBoolean("thumb")).containsExactlyInAnyOrder(false, true);
		assertThat(files).extracting(f -> f.getLong("length")).contains((long)"png-content".length());
		assertThat(db.getCollection("fs.files").countDocuments(Filters.eq("metadata.attachId", 32))).isZero();
	}

	@Test
	@Order(8)
	void otherTablesKeepTheirIdsAndLinkTablesGetGeneratedIds()
	{
		assertThat(find("forums", 8).getInteger("categoriesId")).isEqualTo(5);
		assertThat(find("topics", 10).getInteger("topicVoteId")).isEqualTo(1);
		assertThat(find("categories", 5).getString("title")).isEqualTo("Second category");
		Document banner = find("banner", 1);
		assertThat(banner).containsEntry("name", "Sponsor").containsEntry("placement", 2).containsEntry("weight", 60)
			.containsEntry("active", true).containsEntry("url", "https://example.com").containsEntry("width", 468);
		assertThat(banner.keySet()).noneMatch(k -> k.startsWith("banner"));
		assertThat(find("bookmarks", 1)).containsEntry("publicVisible", true).containsEntry("relationType", 2);
		assertThat(find("bookmarks", 2)).containsEntry("publicVisible", false);
		assertThat(find("karma", 1)).containsEntry("postUserId", 3).containsEntry("fromUserId", 4).containsEntry("points", 5);
		assertThat(find("groups", 3)).containsEntry("groupName", "Moderators").containsEntry("parentId", 1);
		assertThat(find("sessions", 3)).containsEntry("sessionId", "5F2C1A9B").containsEntry("sessionTime", 900000)
			.containsEntry("sessionLoggedInt", 1).doesNotContainKey("sessionUserId");
		assertThat(find("moderation_log", 1).getString("logOriginalMessage")).isEqualTo("I voted <b>Java</b>!!!");
		assertThat(find("moderation_log", 1)).containsEntry("userId", 2).containsEntry("logType", 1).containsEntry("postId", 12)
			.containsEntry("topicId", 10).containsEntry("postUserId", 4).containsEntry("logDescription", "Edited for language")
			.containsEntry("logDate", Date.from(Instant.parse("2024-03-02T19:00:00Z")));
		assertThat(find("mail_integration", 1)).containsEntry("forumId", 1).containsEntry("popPort", 995).containsEntry("popSsl", 1)
			.containsEntry("forumEmail", "general@example.com").containsEntry("popHost", "pop.example.com");
		assertThat(find("mail_integration", 2)).containsEntry("forumId", 2).containsEntry("popUsername", "news");
		assertThat(db.getCollection("smilies").countDocuments()).isEqualTo(LegacySqlServer.count("SELECT COUNT(*) FROM jforum_smilies"));
		assertThat(db.getCollection("topics_watch").find().map(d -> d.get("_id")).into(new java.util.ArrayList<>()))
			.containsExactlyInAnyOrder(1, 2);
		assertThat(find("topics_watch", 1)).containsEntry("topicId", 10).containsEntry("userId", 3);
	}

	@Test
	@Order(9)
	void sequencesStartAtTheHighestMigratedId()
	{
		assertThat(find("sequences", "posts").getInteger("value")).isEqualTo(12);
		assertThat(find("sequences", "users").getInteger("value")).isEqualTo(7);
		assertThat(find("sequences", "attachments").getInteger("value")).isEqualTo(32);
		assertThat(find("sequences", "polls").getInteger("value")).isEqualTo(4);
		assertThat(report.sequences).allMatch(s -> s.ok());
	}

	@Test
	@Order(10)
	void rerunWithoutDropIsIdempotentAndKeepsHigherSequences() throws Exception
	{
		db.getCollection("sequences").updateOne(Filters.eq("_id", "posts"), new Document("$set", new Document("value", 100)));
		ParityReport again = new Migration(options("jforum", false, false, 500, attachments, work.resolve("report2"))).run();
		assertThat(again.isOk()).as(again.toText()).isTrue();
		assertThat(find("sequences", "posts").getInteger("value")).isEqualTo(100);
		assertThat(db.getCollection("fs.files").countDocuments()).isEqualTo(3);
	}

	@Test
	@Order(11)
	void cliExitsNonZeroOnMismatch() throws Exception
	{
		db.getCollection("topics").insertOne(new Document("_id", 999).append("topicTitle", "not in the source"));
		String[] args = {
			"--source.url=" + LegacySqlServer.jdbcUrl(),
			"--source.user=" + LegacySqlServer.CONTAINER.getUsername(),
			"--source.password=" + LegacySqlServer.CONTAINER.getPassword(),
			"--target.uri=" + MONGO.getConnectionString() + "/jforum",
			"--batch-size=50",
			"--report-dir=" + work.resolve("report3") };
		int exit = SpringApplication.exit(SpringApplication.run(MigrationApplication.class, args));
		assertThat(exit).isEqualTo(MigrationCommand.EXIT_MISMATCH);
		assertThat(Files.readString(work.resolve("report3").resolve(Migration.TEXT_REPORT)))
			.contains("STATUS: MISMATCH").containsPattern("DIFF\\s+jforum_topics ");

		String[] fresh = args.clone();
		fresh[3] = "--target.uri=" + MONGO.getConnectionString() + "/jforum_cli";
		fresh[5] = "--report-dir=" + work.resolve("report4");
		String[] withDrop = java.util.Arrays.copyOf(fresh, fresh.length + 1);
		withDrop[fresh.length] = "--drop-target";
		assertThat(SpringApplication.exit(SpringApplication.run(MigrationApplication.class, withDrop))).isZero();
		assertThat(client.getDatabase("jforum_cli").getCollection("posts").countDocuments()).isEqualTo(3);
	}

	@Test
	@Order(12)
	void dryRunWritesNothing() throws Exception
	{
		ParityReport dry = new Migration(options("jforum_dry", false, true, 3, attachments, work.resolve("dry"))).run();
		assertThat(dry.isOk()).as(dry.toText()).isTrue();
		assertThat(dry.entry("jforum_users", "documents").targetCount()).isEqualTo(5);
		assertThat(dry.entry("files in " + attachments, "GridFS files").targetCount()).isEqualTo(3);
		assertThat(dry.sequences).anySatisfy(s -> assertThat(s).isEqualTo(new SequenceEntry("posts", 12, 12L, true)));
		assertThat(client.getDatabase("jforum_dry").listCollectionNames().into(new java.util.ArrayList<>())).isEmpty();
	}
}
