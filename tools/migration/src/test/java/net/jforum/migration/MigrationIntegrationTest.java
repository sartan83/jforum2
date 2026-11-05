package net.jforum.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.mongodb.client.gridfs.model.GridFSFile;

import net.jforum.domain.Attachment;
import net.jforum.domain.AvatarType;
import net.jforum.domain.Bookmark;
import net.jforum.domain.BookmarkType;
import net.jforum.domain.Forum;
import net.jforum.domain.Group;
import net.jforum.domain.ModerationLog;
import net.jforum.domain.ModerationLogType;
import net.jforum.domain.Post;
import net.jforum.domain.PrivateMessage;
import net.jforum.domain.PrivateMessageType;
import net.jforum.domain.QuotaLimit;
import net.jforum.domain.Role;
import net.jforum.domain.Smilie;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicStatus;
import net.jforum.domain.TopicType;
import net.jforum.domain.TopicWatch;
import net.jforum.domain.User;

/** End-to-end migration: SQL Server 2022 (legacy schema + dump + fixtures) to MongoDB 8. */
@SpringBootTest(properties = "migration.enabled=false")
class MigrationIntegrationTest {

    static final MongoDBContainer MONGO = new MongoDBContainer(
        DockerImageName.parse(System.getProperty("mongodb.image", "mongo:8")).asCompatibleSubstituteFor("mongo"));

    static final Path UPLOAD_DIR;

    static {
        MONGO.start();
        LegacySqlServer.start();
        try {
            UPLOAD_DIR = Files.createTempDirectory("jforum-upload");
            Files.createDirectories(UPLOAD_DIR.resolve("2024/3"));
            Files.writeString(UPLOAD_DIR.resolve("2024/3/1_abc123.png"), "PNG-12bytes!");
            Files.writeString(UPLOAD_DIR.resolve("2024/3/5_def456.txt"), "hello notes");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", LegacySqlServer::jdbcUrl);
        registry.add("spring.datasource.username", LegacySqlServer.CONTAINER::getUsername);
        registry.add("spring.datasource.password", LegacySqlServer.CONTAINER::getPassword);
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("jforum"));
        registry.add("migration.upload-dir", UPLOAD_DIR::toString);
        registry.add("migration.batch-size", () -> "2");
    }

    @Autowired
    private SqlServerToMongoMigration migration;

    @Autowired
    private MigrationProperties properties;

    @Autowired
    private MongoTemplate mongo;

    @BeforeEach
    void cleanTarget() {
        properties.setDryRun(false);
        properties.setDropTarget(true);
        mongo.getDb().drop();
    }

    @AfterEach
    void resetProperties() {
        properties.setDryRun(false);
        properties.setDropTarget(false);
    }

    @Test
    void migratesEveryTableAndVerifies() {
        MigrationReport report = migration.migrate();

        assertThat(report.getMismatches()).isEmpty();
        assertThat(report.isVerified()).isTrue();
        assertThat(report.getSkippedTables()).isEmpty();
        assertThat(report.getChecks()).hasSizeGreaterThan(30);
        assertThat(report.table("jforum_smilies").rows()).isEqualTo(32);
        assertThat(report.table("jforum_users + jforum_user_groups").documents()).isEqualTo(5);

        assertThat(mongo.getCollection("users").countDocuments()).isEqualTo(5);
        assertThat(mongo.getCollection("groups").countDocuments()).isEqualTo(3);
        assertThat(mongo.getCollection("posts").countDocuments()).isEqualTo(3);
        assertThat(mongo.getCollection("smilies").countDocuments()).isEqualTo(32);
        assertThat(mongo.getCollection("private_messages").countDocuments()).isEqualTo(2);
        assertThat(mongo.getCollection("attachments").countDocuments()).isEqualTo(3);
    }

    @Test
    void usersKeepIdsPasswordsFlagsAndGroups() {
        migration.migrate();

        User admin = mongo.findById(2, User.class);
        assertThat(admin.getUsername()).isEqualTo("Admin");
        assertThat(admin.getPassword()).isEqualTo("21232f297a57a5a743894a0e4a801fc3");
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getGroupIds()).containsExactly(2);
        assertThat(admin.getTotalPosts()).isEqualTo(1);
        assertThat(admin.getRankId()).isNull();
        assertThat(admin.getDateFormat()).isEqualTo("%d/%M/%Y %H:%i");

        User alice = mongo.findById(3, User.class);
        assertThat(alice.getGroupIds()).containsExactly(1, 3);
        assertThat(alice.getEmail()).isEqualTo("alice@example.com");
        assertThat(alice.getRegistrationDate()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
        assertThat(alice.getRankId()).isEqualTo(2);
        assertThat(alice.getKarmaScore()).isEqualTo(4.5);
        assertThat(alice.getKarmaVotes()).isEqualTo(2);
        assertThat(alice.isViewEmail()).isTrue();
        assertThat(alice.isHtmlEnabled()).isTrue();
        assertThat(alice.isNotifyAlways()).isTrue();
        assertThat(alice.isAttachSignature()).isTrue();
        assertThat(alice.getAvatarType()).isEqualTo(AvatarType.fromLegacy(1));
        assertThat(alice.getGender()).isEqualTo("F");
        assertThat(alice.getSignature()).isEqualTo("Alice [b]sig[/b]");
        assertThat(alice.getActivationKey()).isNull();

        User bob = mongo.findById(4, User.class);
        assertThat(bob.isActive()).isFalse();
        assertThat(bob.getActivationKey()).isEqualTo("k3y");
        assertThat(bob.isPrivateMessagesEnabled()).isFalse();
        assertThat(bob.isNotifyAlways()).isFalse();

        // user_active NULL without a pending activation key: account usable, like legacy without email validation
        assertThat(mongo.findById(7, User.class).isActive()).isTrue();

        Document raw = mongo.getCollection("users").find(new Document("_id", 3)).first();
        assertThat(raw.get("_id")).isInstanceOf(Integer.class);
        assertThat(raw.getString("_class")).isEqualTo(User.class.getName());
    }

    @Test
    void groupsEmbedRolesWithValues() {
        migration.migrate();

        Group moderators = mongo.findById(3, Group.class);
        assertThat(moderators.getName()).isEqualTo("Moderators");
        assertThat(moderators.getParentId()).isEqualTo(1);
        assertThat(moderators.getRoles()).extracting(Role::getName)
            .containsExactly("perm_moderation_forums", "perm_moderation");
        assertThat(moderators.getRoles().get(0).getValues()).containsExactly("1", "2");
        assertThat(moderators.getRoles().get(1).getValues()).isEmpty();

        Group general = mongo.findById(1, Group.class);
        assertThat(general.getParentId()).isNull();
        assertThat(general.getRoles()).extracting(Role::getName).contains("perm_forum", "perm_category");
        assertThat(general.getRoles()).filteredOn(r -> r.getName().equals("perm_forum"))
            .singleElement().satisfies(r -> assertThat(r.getValues()).containsExactly("1"));
        Group admin = mongo.findById(2, Group.class);
        assertThat(admin.getRoles()).extracting(Role::getName).contains("perm_administration");
        assertThat(mongo.findAll(Group.class)).flatExtracting(Group::getRoles).extracting(Role::getName)
            .doesNotContain("perm_vote_user_level");
    }

    @Test
    void forumsTopicsPollsAndPosts() {
        migration.migrate();

        Forum forum = mongo.findById(8, Forum.class);
        assertThat(forum.getCategoryId()).isEqualTo(5);
        assertThat(forum.getTotalTopics()).isEqualTo(1);
        assertThat(forum.getTotalPosts()).isEqualTo(2);
        assertThat(forum.getLastPostId()).isEqualTo(12);
        assertThat(forum.getLastPostInfo().getUsername()).isEqualTo("bob");
        assertThat(forum.getLastPostInfo().getTopicId()).isEqualTo(10);
        assertThat(forum.getLastPostInfo().getTopicReplies()).isEqualTo(1);
        assertThat(forum.getLastPostInfo().getPostTime()).isEqualTo(Instant.parse("2024-03-02T18:45:10Z"));

        Topic topic = mongo.findById(10, Topic.class);
        assertThat(topic.getForumId()).isEqualTo(8);
        assertThat(topic.getUsername()).isEqualTo("alice");
        assertThat(topic.getStatus()).isEqualTo(TopicStatus.fromLegacy(1));
        assertThat(topic.getType()).isEqualTo(TopicType.fromLegacy(2));
        assertThat(topic.getFirstPostId()).isEqualTo(11);
        assertThat(topic.getLastPostId()).isEqualTo(12);
        assertThat(topic.getLastPostUserId()).isEqualTo(4);
        assertThat(topic.getLastPostUsername()).isEqualTo("bob");
        assertThat(topic.isHasAttach()).isTrue();
        assertThat(topic.getViews()).isEqualTo(42);
        assertThat(topic.getPoll().getId()).isEqualTo(4);
        assertThat(topic.getPoll().getLabel()).isEqualTo("Favourite language?");
        assertThat(topic.getPoll().getStartTime()).isEqualTo(Instant.ofEpochSecond(1709294400));
        assertThat(topic.getPoll().getLength()).isEqualTo(7);
        assertThat(topic.getPoll().getOptions()).extracting(o -> o.getText() + "=" + o.getVoteCount())
            .containsExactly("Java=2", "Kotlin=0", "Scala=1");
        assertThat(topic.getPoll().getVoters()).extracting(v -> v.getUserId() + "@" + v.getIp())
            .containsExactly("3@10.0.0.1", "4@10.0.0.2");
        assertThat(mongo.findById(1, Topic.class).getPoll()).isNull();

        Post welcome = mongo.findById(1, Post.class);
        assertThat(welcome.getText()).startsWith("[b]Congratulations![/b]");
        assertThat(welcome.getUsername()).isEqualTo("Admin");
        assertThat(welcome.getTime()).isEqualTo(Instant.parse("2005-01-04T16:59:54Z"));

        Post reply = mongo.findById(12, Post.class);
        assertThat(reply.getSubject()).isEqualTo("Re: What do you prefer?");
        assertThat(reply.getText()).isEqualTo("I voted <i>Java</i>.");
        assertThat(reply.isBbCodeEnabled()).isFalse();
        assertThat(reply.isHtmlEnabled()).isTrue();
        assertThat(reply.isSmiliesEnabled()).isFalse();
        assertThat(reply.isSignatureEnabled()).isFalse();
        assertThat(reply.isModerate()).isTrue();
        assertThat(reply.getEditCount()).isEqualTo(2);
        assertThat(reply.getUserIp()).isEqualTo("10.0.0.2");

        Post first = mongo.findById(11, Post.class);
        assertThat(first.isHasAttachments()).isTrue();
        assertThat(first.getKarma()).isEqualTo(3);
    }

    @Test
    void privateMessagesAttachmentsAndGridFs() throws IOException {
        MigrationReport report = migration.migrate();

        PrivateMessage inbox = mongo.findById(20, PrivateMessage.class);
        assertThat(inbox.getType()).isEqualTo(PrivateMessageType.fromLegacy(1));
        assertThat(inbox.getFromUserId()).isEqualTo(3);
        assertThat(inbox.getToUserId()).isEqualTo(4);
        assertThat(inbox.getText()).isEqualTo("See the attached [i]file[/i].");
        assertThat(inbox.getDate()).isEqualTo(Instant.parse("2024-03-05T09:15:00Z"));
        assertThat(inbox.isSignatureEnabled()).isFalse();
        assertThat(inbox.isHasAttachments()).isTrue();
        assertThat(mongo.findById(21, PrivateMessage.class).getType()).isEqualTo(PrivateMessageType.fromLegacy(2));

        Attachment png = mongo.findById(30, Attachment.class);
        assertThat(png.getPostId()).isEqualTo(11);
        assertThat(png.getPrivmsgId()).isNull();
        assertThat(png.getDescription()).isEqualTo("Architecture diagram");
        assertThat(png.getRealFilename()).isEqualTo("diagram.png");
        assertThat(png.getDownloadCount()).isEqualTo(5);
        assertThat(png.isThumb()).isTrue();
        assertThat(png.getExtensionId()).isEqualTo(1);
        assertThat(content(png.getGridFsId())).isEqualTo("PNG-12bytes!");

        Attachment pmFile = mongo.findById(31, Attachment.class);
        assertThat(pmFile.getPostId()).isNull();
        assertThat(pmFile.getPrivmsgId()).isEqualTo(20);
        assertThat(content(pmFile.getGridFsId())).isEqualTo("hello notes");

        Attachment missing = mongo.findById(32, Attachment.class);
        assertThat(missing.getGridFsId()).isNull();
        assertThat(missing.getFilesize()).isEqualTo(99);
        assertThat(report.getAttachmentFilesStored()).isEqualTo(2);
        assertThat(report.getAttachmentFilesMissing()).isEqualTo(1);
    }

    @Test
    void otherCollectionsAndCounters() {
        MigrationReport report = migration.migrate();

        Bookmark bookmark = mongo.findById(2, Bookmark.class);
        assertThat(bookmark.getRelationType()).isEqualTo(BookmarkType.fromLegacy(1));
        assertThat(bookmark.isPublicVisible()).isFalse();
        TopicWatch watch = mongo.findById("10:3", TopicWatch.class);
        assertThat(watch.isRead()).isTrue();
        assertThat(mongo.findById("10:4", TopicWatch.class).isRead()).isFalse();
        assertThat(mongo.getCollection("forum_watch").find(new Document("_id", "8:3")).first()).isNotNull();

        QuotaLimit quota = mongo.findById(1, QuotaLimit.class);
        assertThat(quota.getGroupIds()).containsExactly(1, 3);
        assertThat(quota.getType()).isEqualTo(2);

        ModerationLog log = mongo.findById(1, ModerationLog.class);
        assertThat(log.getUsername()).isEqualTo("Admin");
        assertThat(log.getPostUsername()).isEqualTo("bob");
        assertThat(log.getType()).isEqualTo(ModerationLogType.fromLegacy(1));
        assertThat(log.getOriginalMessage()).isEqualTo("I voted <b>Java</b>!!!");

        Smilie smile = mongo.findById(1, Smilie.class);
        assertThat(smile.getCode()).isEqualTo(":)");
        assertThat(smile.getUrl()).isEqualTo("/images/smilies/3b63d1616c5dfcf29f8a7a031aaa7cad.gif");

        Document banlist = mongo.getCollection("banlist").find(new Document("_id", 2)).first();
        assertThat(banlist.getInteger("userId")).isEqualTo(4);
        assertThat(banlist.containsKey("email")).isFalse();
        assertThat(mongo.getCollection("config").find(new Document("_id", "most.users.ever.online")).first()
            .getString("value")).isEqualTo("12");
        assertThat(mongo.getCollection("banners").find(new Document("_id", 1)).first().getInteger("width")).isEqualTo(468);
        assertThat(mongo.getCollection("api_keys").find(new Document("_id", 1)).first().getString("apiKey"))
            .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(mongo.getCollection("extension_groups").find(new Document("_id", 2)).first().getBoolean("allow"))
            .isFalse();

        assertThat(counter("users")).isEqualTo(7);
        assertThat(counter("topics")).isEqualTo(10);
        assertThat(counter("posts")).isEqualTo(12);
        assertThat(counter("private_messages")).isEqualTo(21);
        assertThat(counter("attachments")).isEqualTo(32);
        assertThat(counter("smilies")).isEqualTo(32);
        assertThat(counter("polls")).isEqualTo(4);
        assertThat(report.getCounters()).containsEntry("forums", 8).containsEntry("groups", 3);

        assertThat(mongo.getCollection("users").listIndexes().into(new java.util.ArrayList<>()))
            .anySatisfy(i -> assertThat(i.getBoolean("unique", false) && i.get("key", Document.class).containsKey("username")).isTrue());
    }

    @Test
    void rerunIsIdempotentAndKeepsHigherCounters() {
        migration.migrate();
        mongo.getCollection("counters").updateOne(new Document("_id", "posts"), new Document("$set", new Document("seq", 500)));
        long files = mongo.getCollection("fs.files").countDocuments();

        properties.setDropTarget(false);
        MigrationReport second = migration.migrate();

        assertThat(second.isVerified()).isTrue();
        assertThat(mongo.getCollection("users").countDocuments()).isEqualTo(5);
        assertThat(mongo.getCollection("posts").countDocuments()).isEqualTo(3);
        assertThat(mongo.getCollection("topic_watch").countDocuments()).isEqualTo(2);
        assertThat(mongo.getCollection("fs.files").countDocuments()).isEqualTo(files).isEqualTo(2);
        assertThat(counter("posts")).isEqualTo(500);
        assertThat(counter("users")).isEqualTo(7);
    }

    @Test
    void dryRunWritesNothing() {
        properties.setDryRun(true);
        MigrationReport report = migration.migrate();

        assertThat(report.isDryRun()).isTrue();
        assertThat(report.table("jforum_posts + jforum_posts_text").rows()).isEqualTo(3);
        assertThat(report.table("jforum_privmsgs + jforum_privmsgs_text").documents()).isEqualTo(2);
        assertThat(report.getChecks()).isEmpty();
        assertThat(report.getAttachmentFilesStored()).isEqualTo(2);
        assertThat(mongo.getCollectionNames()).isEmpty();
        assertThat(MigrationRunner.format(report)).contains("DRY RUN").contains("jforum_users");
    }

    @Test
    void verificationDetectsMissingDocuments() {
        migration.migrate();
        mongo.remove(Query.query(Criteria.where("_id").is(12)), Post.class);

        properties.setDryRun(false);
        MigrationReport report = new MigrationReport(false);
        new MigrationVerifier(mongo, legacySource(), report).verify();

        assertThat(report.isVerified()).isFalse();
        assertThat(report.getMismatches()).extracting(MigrationReport.Check::target).contains("posts");
    }

    @Autowired
    private LegacySource legacySource;

    private LegacySource legacySource() {
        return legacySource;
    }

    private int counter(String name) {
        return mongo.getCollection("counters").find(new Document("_id", name)).first().getInteger("seq");
    }

    private String content(String gridFsId) throws IOException {
        assertThat(gridFsId).isNotNull();
        GridFsTemplate gridFs = new GridFsTemplate(mongo.getMongoDatabaseFactory(), mongo.getConverter());
        GridFSFile file = gridFs.findOne(Query.query(Criteria.where("_id").is(new ObjectId(gridFsId))));
        assertThat(file.getMetadata().getInteger("attachmentId")).isNotNull();
        try (InputStream in = gridFs.getResource(file).getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unused")
    private static List<String> names(List<Role> roles) {
        return roles.stream().map(Role::getName).toList();
    }
}
