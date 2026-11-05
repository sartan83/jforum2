package net.jforum.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import com.mongodb.client.gridfs.model.GridFSFile;

import net.jforum.domain.ApiKey;
import net.jforum.domain.Attachment;
import net.jforum.domain.AttachmentExtension;
import net.jforum.domain.AttachmentExtensionGroup;
import net.jforum.domain.AvatarType;
import net.jforum.domain.Banlist;
import net.jforum.domain.Banner;
import net.jforum.domain.Bookmark;
import net.jforum.domain.BookmarkType;
import net.jforum.domain.Category;
import net.jforum.domain.ConfigEntry;
import net.jforum.domain.Forum;
import net.jforum.domain.ForumWatch;
import net.jforum.domain.Group;
import net.jforum.domain.Karma;
import net.jforum.domain.LastPostInfo;
import net.jforum.domain.ModerationLog;
import net.jforum.domain.ModerationLogType;
import net.jforum.domain.Poll;
import net.jforum.domain.PollOption;
import net.jforum.domain.PollVoter;
import net.jforum.domain.Post;
import net.jforum.domain.PrivateMessage;
import net.jforum.domain.PrivateMessageType;
import net.jforum.domain.QuotaLimit;
import net.jforum.domain.Ranking;
import net.jforum.domain.Role;
import net.jforum.domain.Smilie;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicStatus;
import net.jforum.domain.TopicType;
import net.jforum.domain.TopicWatch;
import net.jforum.domain.User;

/**
 * Copies a legacy JForum 2.1.8 SQL Server database into MongoDB using the application's own domain classes
 * ({@code net.jforum.domain}), so the documents have exactly the shape the application reads.
 * Every document is upserted by its legacy id: re-running the migration is idempotent.
 */
@Component
public class SqlServerToMongoMigration {

    private static final Logger log = LoggerFactory.getLogger(SqlServerToMongoMigration.class);

    /** Collections written by the migration (all with the legacy numeric / natural id as {@code _id}). */
    static final List<Class<?>> DOMAIN_TYPES = List.of(User.class, Group.class, Category.class, Forum.class,
        Topic.class, Post.class, PrivateMessage.class, Attachment.class, Karma.class, Bookmark.class, TopicWatch.class,
        ForumWatch.class, Ranking.class, Smilie.class, Banlist.class, ConfigEntry.class, ModerationLog.class,
        AttachmentExtensionGroup.class, AttachmentExtension.class, QuotaLimit.class, Banner.class, ApiKey.class);

    /** Collections whose sequence ({@code counters._id}) continues after the imported ids. */
    static final List<String> SEQUENCES = List.of("users", "groups", "categories", "forums", "topics", "posts",
        "private_messages", "attachments", "karma", "bookmarks", "rankings", "smilies", "banlist", "moderation_log",
        "extension_groups", "extensions", "quota_limits", "banners", "api_keys");

    static final String COUNTERS = "counters";
    static final String POLL_SEQUENCE = "polls";

    private static final Pattern IMG_SRC = Pattern.compile("src=\\\\?\"([^\"\\\\]+)");

    @FunctionalInterface
    interface Mapper<T> {
        T map(SourceRow row) throws SQLException;
    }

    private final MongoTemplate mongo;
    private final LegacySource legacy;
    private final MigrationProperties properties;
    private MigrationReport report;

    public SqlServerToMongoMigration(MongoTemplate mongo, LegacySource legacy, MigrationProperties properties) {
        this.mongo = mongo;
        this.legacy = legacy;
        this.properties = properties;
    }

    public MigrationReport migrate() {
        legacy.refresh();
        report = new MigrationReport(properties.isDryRun());
        log.info("Starting JForum SQL Server -> MongoDB migration (dryRun={}, dropTarget={}, batchSize={}, uploadDir={})",
            properties.isDryRun(), properties.isDropTarget(), properties.getBatchSize(), properties.getUploadDir());
        if (!properties.isDryRun()) {
            prepareTarget();
        }
        migrateGroups();
        migrateUsers();
        migrateCategories();
        migrateForums();
        migrateTopics();
        migratePosts();
        migratePrivateMessages();
        migrateAttachments();
        migrateKarma();
        migrateBookmarks();
        migrateTopicWatches();
        migrateForumWatches();
        migrateRankings();
        migrateSmilies();
        migrateBanlist();
        migrateConfig();
        migrateModerationLog();
        migrateExtensionGroups();
        migrateExtensions();
        migrateQuotaLimits();
        migrateBanners();
        migrateApiKeys();
        if (!properties.isDryRun()) {
            updateCounters();
            new MigrationVerifier(mongo, legacy, report).verify();
        }
        return report;
    }

    // ------------------------------------------------------------------ target preparation

    private void prepareTarget() {
        if (properties.isDropTarget()) {
            for (Class<?> type : DOMAIN_TYPES) {
                mongo.dropCollection(type);
            }
            mongo.dropCollection(COUNTERS);
            mongo.dropCollection(properties.getGridfsBucket() + ".files");
            mongo.dropCollection(properties.getGridfsBucket() + ".chunks");
            log.info("Dropped target collections, counters and GridFS bucket '{}'", properties.getGridfsBucket());
        }
        MongoMappingContext context = (MongoMappingContext) mongo.getConverter().getMappingContext();
        IndexResolver resolver = IndexResolver.create(context);
        for (Class<?> type : DOMAIN_TYPES) {
            IndexOperations ops = mongo.indexOps(type);
            resolver.resolveIndexFor(type).forEach(ops::createIndex);
        }
    }

    // ------------------------------------------------------------------ generic copy

    private <T> void copy(String table, String sql, Class<T> type, Function<T, Object> idOf, Mapper<T> mapper) {
        copy(table, table, sql, type, idOf, mapper);
    }

    private <T> void copy(String source, String table, String sql, Class<T> type, Function<T, Object> idOf,
                          Mapper<T> mapper) {
        String target = mongo.getCollectionName(type);
        if (!legacy.exists(table)) {
            report.getSkippedTables().add(table);
            log.warn("{} not found in the source database: {} skipped", table, target);
            return;
        }
        long start = System.currentTimeMillis();
        BatchWriter<T> writer = new BatchWriter<>(mongo, type, idOf, properties.getBatchSize(), properties.isDryRun());
        long[] rows = {0};
        legacy.stream(sql, row -> {
            rows[0]++;
            T doc = mapper.map(row);
            if (doc != null) {
                writer.add(doc);
            }
        });
        writer.flush();
        long millis = System.currentTimeMillis() - start;
        report.getTables().add(new MigrationReport.TableResult(source, target, rows[0], writer.count(), millis));
        log.info("{} -> {}: {} rows read, {} documents {} ({} ms)", source, target, rows[0], writer.count(),
            properties.isDryRun() ? "to write" : "upserted", millis);
    }

    private boolean has(String table) {
        return legacy.exists(table);
    }

    // ------------------------------------------------------------------ groups & users

    private void migrateGroups() {
        Map<Integer, Map<String, Role>> roles = new HashMap<>();
        if (has("jforum_roles")) {
            String sql = has("jforum_role_values")
                ? "SELECT r.group_id, r.name, rv.role_value FROM jforum_roles r "
                    + "LEFT JOIN jforum_role_values rv ON rv.role_id = r.role_id "
                    + "WHERE r.group_id > 0 ORDER BY r.group_id, r.role_id, rv.role_value"
                : "SELECT r.group_id, r.name, NULL AS role_value FROM jforum_roles r "
                    + "WHERE r.group_id > 0 ORDER BY r.group_id, r.role_id";
            legacy.stream(sql, row -> {
                String name = row.trimmed("name");
                if (name == null) {
                    return;
                }
                Role role = roles.computeIfAbsent(row.integer("group_id"), g -> new LinkedHashMap<>())
                    .computeIfAbsent(name, n -> {
                        Role r = new Role();
                        r.setName(n);
                        return r;
                    });
                String value = row.trimmed("role_value");
                if (value != null && !role.getValues().contains(value)) {
                    role.getValues().add(value);
                }
            });
        }
        copy("jforum_groups + jforum_roles + jforum_role_values", "jforum_groups",
            "SELECT * FROM jforum_groups ORDER BY group_id", Group.class, Group::getId, row -> {
                Group g = new Group();
                g.setId(row.integer("group_id"));
                g.setName(row.string("group_name"));
                g.setDescription(row.string("group_description"));
                g.setParentId(row.id("parent_id"));
                Map<String, Role> groupRoles = roles.get(g.getId());
                g.setRoles(groupRoles == null ? new ArrayList<>() : new ArrayList<>(groupRoles.values()));
                return g;
            });
    }

    private void migrateUsers() {
        Map<Integer, List<Integer>> userGroups = new HashMap<>();
        if (has("jforum_user_groups")) {
            legacy.stream("SELECT DISTINCT user_id, group_id FROM jforum_user_groups ORDER BY user_id, group_id",
                row -> userGroups.computeIfAbsent(row.integer("user_id"), u -> new ArrayList<>())
                    .add(row.integer("group_id")));
        }
        String karmaVotes = has("jforum_karma")
            ? "(SELECT COUNT(*) FROM jforum_karma k WHERE k.post_user_id = u.user_id)"
            : "0";
        copy("jforum_users + jforum_user_groups", "jforum_users",
            "SELECT u.*, " + karmaVotes + " AS migration_karma_votes FROM jforum_users u ORDER BY u.user_id",
            User.class, User::getId, row -> {
                User u = new User();
                u.setId(row.integer("user_id"));
                u.setUsername(row.string("username"));
                u.setEmail(row.trimmed("user_email"));
                u.setPassword(row.trimmed("user_password"));
                String activationKey = row.trimmed("user_actkey");
                Integer active = row.integer("user_active");
                // NULL = never required activation (legacy addNew doesn't set it); pending activation keeps a key
                u.setActive(active == null ? activationKey == null : active == 1);
                u.setDeleted(row.flag("deleted"));
                u.setActivationKey(activationKey);
                u.setSecurityHash(row.trimmed("security_hash"));
                u.setRegistrationDate(row.instant("user_regdate"));
                u.setLastVisit(row.instant("user_lastvisit"));
                u.setTotalPosts(row.intValue("user_posts", 0));
                u.setRankId(row.id("rank_id"));
                u.setLang(row.trimmed("user_lang"));
                u.setDateFormat(row.trimmed("user_dateformat"));
                u.setTimezone(row.trimmed("user_timezone"));
                u.setAvatar(row.trimmed("user_avatar"));
                u.setAvatarType(AvatarType.fromLegacy(row.intValue("user_avatar_type", 0)));
                u.setWebsite(row.trimmed("user_website"));
                u.setFrom(row.trimmed("user_from"));
                u.setOccupation(row.trimmed("user_occ"));
                u.setInterests(row.trimmed("user_interests"));
                u.setBiography(row.text("user_biography"));
                u.setSignature(row.text("user_sig"));
                u.setIcq(row.trimmed("user_icq"));
                u.setAim(row.trimmed("user_aim"));
                u.setYim(row.trimmed("user_yim"));
                u.setMsnm(row.trimmed("user_msnm"));
                u.setGender(row.trimmed("gender"));
                u.setViewEmail(row.flag("user_viewemail"));
                u.setAttachSignature(row.flag("user_attachsig"));
                u.setHtmlEnabled(row.flag("user_allowhtml"));
                u.setBbCodeEnabled(row.flag("user_allowbbcode"));
                u.setSmiliesEnabled(row.flag("user_allowsmilies"));
                u.setAvatarEnabled(row.flag("user_allowavatar"));
                u.setPrivateMessagesEnabled(row.flag("user_allow_pm"));
                u.setViewOnline(row.flag("user_viewonline"));
                u.setNotifyOnMessagesEnabled(row.flag("user_notify"));
                u.setNotifyAlways(row.flag("user_notify_always"));
                u.setNotifyText(row.flag("user_notify_text"));
                u.setNotifyPrivateMessages(row.flag("user_notify_pm"));
                u.setNewPrivateMessages(row.intValue("user_new_privmsg", 0));
                u.setUnreadPrivateMessages(row.intValue("user_unread_privmsg", 0));
                u.setKarmaScore(row.doubleValue("user_karma"));
                u.setKarmaVotes(row.intValue("migration_karma_votes", 0));
                u.setGroupIds(userGroups.getOrDefault(u.getId(), new ArrayList<>()));
                return u;
            });
    }

    // ------------------------------------------------------------------ categories, forums, topics, posts

    private void migrateCategories() {
        copy("jforum_categories", "SELECT * FROM jforum_categories ORDER BY categories_id", Category.class,
            Category::getId, row -> {
                Category c = new Category();
                c.setId(row.integer("categories_id"));
                c.setTitle(row.string("title"));
                c.setOrder(row.intValue("display_order", 0));
                c.setModerated(row.flag("moderated"));
                return c;
            });
    }

    private void migrateForums() {
        boolean posts = has("jforum_posts");
        String sql = posts
            ? "SELECT f.*, (SELECT COUNT(*) FROM jforum_posts p WHERE p.forum_id = f.forum_id) AS migration_total_posts, "
                + "lp.post_id AS lp_post_id, lp.topic_id AS lp_topic_id, lp.user_id AS lp_user_id, "
                + "lp.post_time AS lp_post_time, lu.username AS lp_username, lt.topic_replies AS lp_topic_replies "
                + "FROM jforum_forums f "
                + "LEFT JOIN jforum_posts lp ON lp.post_id = f.forum_last_post_id "
                + "LEFT JOIN jforum_users lu ON lu.user_id = lp.user_id "
                + "LEFT JOIN jforum_topics lt ON lt.topic_id = lp.topic_id ORDER BY f.forum_id"
            : "SELECT f.* FROM jforum_forums f ORDER BY f.forum_id";
        copy("jforum_forums", sql, Forum.class, Forum::getId, row -> {
            Forum f = new Forum();
            f.setId(row.integer("forum_id"));
            f.setCategoryId(row.id("categories_id"));
            f.setName(row.string("forum_name"));
            f.setDescription(row.string("forum_desc"));
            f.setOrder(row.intValue("forum_order", 0));
            f.setModerated(row.flag("moderated"));
            f.setTotalTopics(row.intValue("forum_topics", 0));
            f.setTotalPosts(row.intValue("migration_total_posts", 0));
            f.setLastPostId(row.id("forum_last_post_id"));
            if (row.id("lp_post_id") != null) {
                LastPostInfo info = new LastPostInfo();
                info.setPostId(row.id("lp_post_id"));
                info.setTopicId(row.id("lp_topic_id"));
                info.setUserId(row.id("lp_user_id"));
                info.setUsername(row.string("lp_username"));
                info.setPostTime(row.instant("lp_post_time"));
                info.setTopicReplies(row.intValue("lp_topic_replies", 0));
                f.setLastPostInfo(info);
            }
            return f;
        });
    }

    private Map<Integer, Poll> loadPolls() {
        Map<Integer, Poll> pollsByTopic = new HashMap<>();
        if (!has("jforum_vote_desc")) {
            return pollsByTopic;
        }
        Map<Integer, List<PollOption>> options = new HashMap<>();
        if (has("jforum_vote_results")) {
            legacy.stream("SELECT * FROM jforum_vote_results ORDER BY vote_id, vote_option_id", row -> {
                PollOption o = new PollOption();
                o.setId(row.intValue("vote_option_id", 0));
                o.setText(row.string("vote_option_text"));
                o.setVoteCount(row.intValue("vote_result", 0));
                options.computeIfAbsent(row.integer("vote_id"), v -> new ArrayList<>()).add(o);
            });
        }
        Map<Integer, List<PollVoter>> voters = new HashMap<>();
        if (has("jforum_vote_voters")) {
            legacy.stream("SELECT * FROM jforum_vote_voters ORDER BY vote_id, vote_user_id", row -> {
                PollVoter v = new PollVoter();
                v.setUserId(row.id("vote_user_id"));
                v.setIp(row.trimmed("vote_user_ip"));
                voters.computeIfAbsent(row.integer("vote_id"), id -> new ArrayList<>()).add(v);
            });
        }
        legacy.stream("SELECT * FROM jforum_vote_desc ORDER BY vote_id", row -> {
            Poll p = new Poll();
            p.setId(row.integer("vote_id"));
            p.setLabel(row.text("vote_text"));
            p.setStartTime(row.instant("vote_start"));
            p.setLength(row.intValue("vote_length", 0));
            p.setOptions(options.getOrDefault(p.getId(), new ArrayList<>()));
            p.setVoters(voters.getOrDefault(p.getId(), new ArrayList<>()));
            pollsByTopic.put(row.integer("topic_id"), p);
        });
        return pollsByTopic;
    }

    private void migrateTopics() {
        Map<Integer, Poll> polls = loadPolls();
        boolean posts = has("jforum_posts");
        String sql = posts
            ? "SELECT t.*, u.username AS migration_username, lp.post_time AS lp_post_time, lp.user_id AS lp_user_id, "
                + "lu.username AS lp_username, CASE WHEN EXISTS (SELECT 1 FROM jforum_posts ap "
                + "WHERE ap.topic_id = t.topic_id AND ap.attach = 1) THEN 1 ELSE 0 END AS migration_has_attach "
                + "FROM jforum_topics t "
                + "LEFT JOIN jforum_users u ON u.user_id = t.user_id "
                + "LEFT JOIN jforum_posts lp ON lp.post_id = t.topic_last_post_id "
                + "LEFT JOIN jforum_users lu ON lu.user_id = lp.user_id ORDER BY t.topic_id"
            : "SELECT t.*, u.username AS migration_username FROM jforum_topics t "
                + "LEFT JOIN jforum_users u ON u.user_id = t.user_id ORDER BY t.topic_id";
        copy("jforum_topics + jforum_vote_desc/results/voters", "jforum_topics", sql, Topic.class, Topic::getId, row -> {
            Topic t = new Topic();
            t.setId(row.integer("topic_id"));
            t.setForumId(row.id("forum_id"));
            t.setTitle(row.string("topic_title"));
            t.setUserId(row.id("user_id"));
            t.setUsername(row.string("migration_username"));
            t.setTime(row.instant("topic_time"));
            t.setViews(row.intValue("topic_views", 0));
            t.setReplies(row.intValue("topic_replies", 0));
            t.setStatus(TopicStatus.fromLegacy(row.intValue("topic_status", 0)));
            t.setType(TopicType.fromLegacy(row.intValue("topic_type", 0)));
            t.setFirstPostId(row.id("topic_first_post_id"));
            t.setLastPostId(row.id("topic_last_post_id"));
            t.setLastPostTime(row.instant("lp_post_time"));
            t.setLastPostUserId(row.id("lp_user_id"));
            t.setLastPostUsername(row.string("lp_username"));
            t.setModerated(row.flag("moderated"));
            t.setHasAttach(row.flag("migration_has_attach"));
            t.setMovedId(row.id("topic_moved_id"));
            t.setPoll(polls.get(t.getId()));
            return t;
        });
    }

    private void migratePosts() {
        String text = has("jforum_posts_text")
            ? "pt.post_text AS migration_text, pt.post_subject AS migration_subject"
            : "NULL AS migration_text, NULL AS migration_subject";
        String textJoin = has("jforum_posts_text") ? "LEFT JOIN jforum_posts_text pt ON pt.post_id = p.post_id " : "";
        String karma = has("jforum_karma")
            ? "(SELECT CASE WHEN COUNT(k.post_id) = 0 THEN 0 ELSE SUM(k.points) / COUNT(k.post_id) END "
                + "FROM jforum_karma k WHERE k.post_id = p.post_id)"
            : "0";
        copy("jforum_posts + jforum_posts_text", "jforum_posts",
            "SELECT p.*, " + text + ", u.username AS migration_username, " + karma + " AS migration_karma "
                + "FROM jforum_posts p " + textJoin + "LEFT JOIN jforum_users u ON u.user_id = p.user_id ORDER BY p.post_id",
            Post.class, Post::getId, row -> {
                Post p = new Post();
                p.setId(row.integer("post_id"));
                p.setTopicId(row.id("topic_id"));
                p.setForumId(row.id("forum_id"));
                p.setUserId(row.id("user_id"));
                p.setUsername(row.string("migration_username"));
                p.setTime(row.instant("post_time"));
                p.setUserIp(row.trimmed("poster_ip"));
                p.setSubject(row.string("migration_subject"));
                p.setText(row.text("migration_text"));
                p.setBbCodeEnabled(row.flag("enable_bbcode"));
                p.setHtmlEnabled(row.flag("enable_html"));
                p.setSmiliesEnabled(row.flag("enable_smilies"));
                p.setSignatureEnabled(row.flag("enable_sig"));
                p.setEditTime(row.instant("post_edit_time"));
                p.setEditCount(row.intValue("post_edit_count", 0));
                p.setModerate(row.flag("need_moderate"));
                p.setHasAttachments(row.flag("attach"));
                p.setKarma(row.intValue("migration_karma", 0));
                return p;
            });
    }

    // ------------------------------------------------------------------ private messages & attachments

    private void migratePrivateMessages() {
        String text = has("jforum_privmsgs_text") ? "t.privmsgs_text AS migration_text" : "NULL AS migration_text";
        String textJoin = has("jforum_privmsgs_text")
            ? "LEFT JOIN jforum_privmsgs_text t ON t.privmsgs_id = pm.privmsgs_id " : "";
        String attach = has("jforum_attach")
            ? "CASE WHEN EXISTS (SELECT 1 FROM jforum_attach a WHERE a.privmsgs_id = pm.privmsgs_id) THEN 1 ELSE 0 END"
            : "0";
        copy("jforum_privmsgs + jforum_privmsgs_text", "jforum_privmsgs",
            "SELECT pm.*, " + text + ", " + attach + " AS migration_has_attach FROM jforum_privmsgs pm " + textJoin
                + "ORDER BY pm.privmsgs_id",
            PrivateMessage.class, PrivateMessage::getId, row -> {
                PrivateMessage pm = new PrivateMessage();
                pm.setId(row.integer("privmsgs_id"));
                pm.setType(PrivateMessageType.fromLegacy(row.intValue("privmsgs_type", 0)));
                pm.setFromUserId(row.id("privmsgs_from_userid"));
                pm.setToUserId(row.id("privmsgs_to_userid"));
                pm.setSubject(row.string("privmsgs_subject"));
                pm.setText(row.text("migration_text"));
                pm.setDate(row.instant("privmsgs_date"));
                pm.setFromIp(row.trimmed("privmsgs_ip"));
                pm.setBbCodeEnabled(row.flag("privmsgs_enable_bbcode"));
                pm.setHtmlEnabled(row.flag("privmsgs_enable_html"));
                pm.setSmiliesEnabled(row.flag("privmsgs_enable_smilies"));
                pm.setSignatureEnabled(row.flag("privmsgs_attach_sig"));
                pm.setHasAttachments(row.flag("migration_has_attach"));
                return pm;
            });
    }

    private void migrateAttachments() {
        GridFsTemplate gridFs = new GridFsTemplate(mongo.getMongoDatabaseFactory(), mongo.getConverter(),
            properties.getGridfsBucket());
        Path uploadDir = properties.getUploadDir() == null || properties.getUploadDir().isBlank()
            ? null : Path.of(properties.getUploadDir()).toAbsolutePath().normalize();
        if (uploadDir == null && has("jforum_attach")) {
            log.warn("migration.upload-dir not set: attachment metadata is migrated without file contents");
        }
        String sql = has("jforum_attach_desc")
            ? "SELECT a.attach_id, a.post_id, a.privmsgs_id, a.user_id, d.* FROM jforum_attach a "
                + "LEFT JOIN jforum_attach_desc d ON d.attach_id = a.attach_id ORDER BY a.attach_id"
            : "SELECT a.* FROM jforum_attach a ORDER BY a.attach_id";
        copy("jforum_attach + jforum_attach_desc (+ files -> GridFS)", "jforum_attach", sql, Attachment.class,
            Attachment::getId, row -> {
                Attachment a = new Attachment();
                a.setId(row.integer("attach_id"));
                a.setPostId(row.id("post_id"));
                a.setPrivmsgId(row.id("privmsgs_id"));
                a.setUserId(row.id("user_id"));
                a.setPhysicalFilename(row.trimmed("physical_filename"));
                a.setRealFilename(row.string("real_filename"));
                a.setDescription(row.string("description"));
                a.setMimetype(row.trimmed("mimetype"));
                a.setFilesize(row.longValue("filesize", 0));
                a.setDownloadCount(row.intValue("download_count", 0));
                a.setUploadTime(row.instant("upload_time"));
                a.setThumb(row.flag("thumb"));
                a.setExtensionId(row.id("extension_id"));
                a.setGridFsId(storeFile(gridFs, uploadDir, a));
                return a;
            });
    }

    /** Stores the legacy file in GridFS (metadata.attachmentId = legacy id); an identical earlier upload is reused. */
    private String storeFile(GridFsTemplate gridFs, Path uploadDir, Attachment a) {
        GridFSFile existing = properties.isDryRun() ? null
            : gridFs.findOne(Query.query(Criteria.where("metadata.attachmentId").is(a.getId())));
        Path file = uploadDir == null || a.getPhysicalFilename() == null ? null
            : uploadDir.resolve(a.getPhysicalFilename()).normalize();
        if (file == null || !file.startsWith(uploadDir) || !Files.isRegularFile(file)) {
            if (uploadDir != null) {
                report.attachmentFileMissing();
                log.warn("Attachment {}: file '{}' not found in {}", a.getId(), a.getPhysicalFilename(), uploadDir);
            }
            return existing == null ? null : existing.getObjectId().toHexString();
        }
        try {
            long size = Files.size(file);
            report.attachmentFileStored();
            if (properties.isDryRun()) {
                return null;
            }
            if (existing != null) {
                if (existing.getLength() == size) {
                    return existing.getObjectId().toHexString();
                }
                gridFs.delete(Query.query(Criteria.where("_id").is(existing.getObjectId())));
            }
            org.bson.Document metadata = new org.bson.Document("attachmentId", a.getId())
                .append("postId", a.getPostId())
                .append("privmsgId", a.getPrivmsgId())
                .append("physicalFilename", a.getPhysicalFilename())
                .append("_contentType", a.getMimetype());
            String filename = a.getRealFilename() != null ? a.getRealFilename() : a.getPhysicalFilename();
            try (InputStream in = Files.newInputStream(file)) {
                ObjectId id = gridFs.store(in, filename, a.getMimetype(), metadata);
                return id.toHexString();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read attachment file " + file, e);
        }
    }

    // ------------------------------------------------------------------ karma, bookmarks, watches

    private void migrateKarma() {
        copy("jforum_karma", "SELECT * FROM jforum_karma ORDER BY karma_id", Karma.class, Karma::getId, row -> {
            Karma k = new Karma();
            k.setId(row.integer("karma_id"));
            k.setPostId(row.id("post_id"));
            k.setTopicId(row.id("topic_id"));
            k.setPostUserId(row.id("post_user_id"));
            k.setFromUserId(row.id("from_user_id"));
            k.setPoints(row.intValue("points", 0));
            k.setRateDate(row.instant("rate_date"));
            return k;
        });
    }

    private void migrateBookmarks() {
        copy("jforum_bookmarks", "SELECT * FROM jforum_bookmarks ORDER BY bookmark_id", Bookmark.class,
            Bookmark::getId, row -> {
                Bookmark b = new Bookmark();
                b.setId(row.integer("bookmark_id"));
                b.setUserId(row.id("user_id"));
                b.setRelationId(row.id("relation_id"));
                b.setRelationType(BookmarkType.fromLegacy(row.intValue("relation_type", 0)));
                b.setPublicVisible(row.flag("public_visible"));
                b.setTitle(row.string("title"));
                b.setDescription(row.string("description"));
                return b;
            });
    }

    static String watchId(Integer targetId, Integer userId) {
        return targetId + ":" + userId;
    }

    private void migrateTopicWatches() {
        copy("jforum_topics_watch",
            "SELECT topic_id, user_id, MAX(is_read) AS is_read FROM jforum_topics_watch GROUP BY topic_id, user_id "
                + "ORDER BY topic_id, user_id",
            TopicWatch.class, TopicWatch::getId, row -> {
                TopicWatch w = new TopicWatch();
                w.setTopicId(row.integer("topic_id"));
                w.setUserId(row.integer("user_id"));
                w.setId(watchId(w.getTopicId(), w.getUserId()));
                w.setRead(row.flag("is_read"));
                return w;
            });
    }

    private void migrateForumWatches() {
        copy("jforum_forums_watch",
            "SELECT DISTINCT forum_id, user_id FROM jforum_forums_watch ORDER BY forum_id, user_id",
            ForumWatch.class, ForumWatch::getId, row -> {
                ForumWatch w = new ForumWatch();
                w.setForumId(row.integer("forum_id"));
                w.setUserId(row.integer("user_id"));
                w.setId(watchId(w.getForumId(), w.getUserId()));
                return w;
            });
    }

    // ------------------------------------------------------------------ lookups & configuration

    private void migrateRankings() {
        copy("jforum_ranks", "SELECT * FROM jforum_ranks ORDER BY rank_id", Ranking.class, Ranking::getId, row -> {
            Ranking r = new Ranking();
            r.setId(row.integer("rank_id"));
            r.setTitle(row.string("rank_title"));
            r.setMin(row.intValue("rank_min", 0));
            r.setSpecial(row.flag("rank_special"));
            r.setImage(row.trimmed("rank_image"));
            return r;
        });
    }

    private void migrateSmilies() {
        copy("jforum_smilies", "SELECT * FROM jforum_smilies ORDER BY smilie_id", Smilie.class, Smilie::getId, row -> {
            Smilie s = new Smilie();
            s.setId(row.integer("smilie_id"));
            s.setCode(row.string("code"));
            s.setDiskName(row.trimmed("disk_name"));
            s.setUrl(smilieUrl(s.getDiskName(), row.string("url")));
            return s;
        });
    }

    /** Same url the application builds ({@code /images/smilies/<disk_name>}); falls back to the legacy img src. */
    static String smilieUrl(String diskName, String legacyUrl) {
        if (diskName != null) {
            return "/images/smilies/" + diskName;
        }
        if (legacyUrl == null) {
            return null;
        }
        Matcher m = IMG_SRC.matcher(legacyUrl);
        return (m.find() ? m.group(1) : legacyUrl).replace("#CONTEXT#", "");
    }

    private void migrateBanlist() {
        copy("jforum_banlist", "SELECT * FROM jforum_banlist ORDER BY banlist_id", Banlist.class, Banlist::getId, row -> {
            Banlist b = new Banlist();
            b.setId(row.integer("banlist_id"));
            b.setUserId(row.id("user_id"));
            b.setIp(row.trimmed("banlist_ip"));
            b.setEmail(row.trimmed("banlist_email"));
            return b;
        });
    }

    private void migrateConfig() {
        copy("jforum_config", "SELECT * FROM jforum_config ORDER BY config_id", ConfigEntry.class, ConfigEntry::getId,
            row -> {
                String name = row.trimmed("config_name");
                if (name == null) {
                    return null;
                }
                ConfigEntry c = new ConfigEntry();
                c.setId(name);
                c.setValue(row.string("config_value"));
                return c;
            });
    }

    private void migrateModerationLog() {
        copy("jforum_moderation_log",
            "SELECT l.*, u.username AS migration_username, pu.username AS migration_post_username "
                + "FROM jforum_moderation_log l LEFT JOIN jforum_users u ON u.user_id = l.user_id "
                + "LEFT JOIN jforum_users pu ON pu.user_id = l.post_user_id ORDER BY l.log_id",
            ModerationLog.class, ModerationLog::getId, row -> {
                ModerationLog m = new ModerationLog();
                m.setId(row.integer("log_id"));
                m.setUserId(row.id("user_id"));
                m.setUsername(row.string("migration_username"));
                m.setDescription(row.text("log_description"));
                m.setOriginalMessage(row.text("log_original_message"));
                m.setDate(row.instant("log_date"));
                m.setType(ModerationLogType.fromLegacy(row.intValue("log_type", 0)));
                m.setPostId(row.id("post_id"));
                m.setTopicId(row.id("topic_id"));
                m.setPostUserId(row.id("post_user_id"));
                m.setPostUsername(row.string("migration_post_username"));
                return m;
            });
    }

    private void migrateExtensionGroups() {
        copy("jforum_extension_groups", "SELECT * FROM jforum_extension_groups ORDER BY extension_group_id",
            AttachmentExtensionGroup.class, AttachmentExtensionGroup::getId, row -> {
                AttachmentExtensionGroup g = new AttachmentExtensionGroup();
                g.setId(row.integer("extension_group_id"));
                g.setName(row.string("name"));
                g.setAllow(row.flag("allow"));
                g.setUploadIcon(row.trimmed("upload_icon"));
                g.setDownloadMode(row.intValue("download_mode", 1));
                return g;
            });
    }

    private void migrateExtensions() {
        copy("jforum_extensions", "SELECT * FROM jforum_extensions ORDER BY extension_id", AttachmentExtension.class,
            AttachmentExtension::getId, row -> {
                AttachmentExtension e = new AttachmentExtension();
                e.setId(row.integer("extension_id"));
                e.setExtensionGroupId(row.id("extension_group_id"));
                e.setDescription(row.string("description"));
                e.setUploadIcon(row.trimmed("upload_icon"));
                e.setExtension(row.trimmed("extension"));
                e.setAllow(row.flag("allow"));
                return e;
            });
    }

    private void migrateQuotaLimits() {
        Map<Integer, List<Integer>> groupsByQuota = new HashMap<>();
        if (has("jforum_attach_quota")) {
            legacy.stream("SELECT DISTINCT quota_limit_id, group_id FROM jforum_attach_quota "
                    + "ORDER BY quota_limit_id, group_id",
                row -> groupsByQuota.computeIfAbsent(row.integer("quota_limit_id"), q -> new ArrayList<>())
                    .add(row.integer("group_id")));
        }
        copy("jforum_quota_limit + jforum_attach_quota", "jforum_quota_limit",
            "SELECT * FROM jforum_quota_limit ORDER BY quota_limit_id", QuotaLimit.class, QuotaLimit::getId, row -> {
                QuotaLimit q = new QuotaLimit();
                q.setId(row.integer("quota_limit_id"));
                q.setDescription(row.string("quota_desc"));
                q.setSize(row.intValue("quota_limit", 0));
                q.setType(row.intValue("quota_type", 1));
                q.setGroupIds(groupsByQuota.getOrDefault(q.getId(), new ArrayList<>()));
                return q;
            });
    }

    private void migrateBanners() {
        copy("jforum_banner", "SELECT * FROM jforum_banner ORDER BY banner_id", Banner.class, Banner::getId, row -> {
            Banner b = new Banner();
            b.setId(row.integer("banner_id"));
            b.setName(row.string("banner_name"));
            b.setPlacement(row.intValue("banner_placement", 0));
            b.setDescription(row.string("banner_description"));
            b.setClicks(row.intValue("banner_clicks", 0));
            b.setViews(row.intValue("banner_views", 0));
            b.setUrl(row.string("banner_url"));
            b.setWeight(row.intValue("banner_weight", 50));
            b.setActive(row.flag("banner_active"));
            b.setComment(row.string("banner_comment"));
            b.setType(row.intValue("banner_type", 0));
            b.setWidth(row.intValue("banner_width", 0));
            b.setHeight(row.intValue("banner_height", 0));
            return b;
        });
    }

    private void migrateApiKeys() {
        copy("jforum_api", "SELECT * FROM jforum_api ORDER BY api_id", ApiKey.class, ApiKey::getId, row -> {
            ApiKey k = new ApiKey();
            k.setId(row.integer("api_id"));
            k.setApiKey(row.trimmed("api_key"));
            k.setValidity(row.instant("api_validity"));
            return k;
        });
    }

    // ------------------------------------------------------------------ counters

    /** Raises {@code counters.<collection>.seq} to max(_id) (never lowers it), like SequenceService.ensureAtLeast. */
    private void updateCounters() {
        for (String collection : SEQUENCES) {
            ensureAtLeast(collection, maxId(collection, "_id"));
        }
        int maxPoll = 0;
        Query withPoll = Query.query(Criteria.where("poll._id").exists(true))
            .with(Sort.by(Sort.Direction.DESC, "poll._id")).limit(1);
        withPoll.fields().include("poll._id");
        org.bson.Document doc = mongo.findOne(withPoll, org.bson.Document.class, "topics");
        if (doc != null && doc.get("poll") instanceof org.bson.Document poll && poll.get("_id") instanceof Number n) {
            maxPoll = n.intValue();
        }
        ensureAtLeast(POLL_SEQUENCE, maxPoll);
    }

    private int maxId(String collection, String field) {
        Query q = Query.query(new Criteria().orOperator(Criteria.where(field).type(16), Criteria.where(field).type(18))).with(Sort.by(Sort.Direction.DESC, field)).limit(1);
        org.bson.Document doc = mongo.findOne(q, org.bson.Document.class, collection);
        return doc == null ? 0 : ((Number) doc.get(field)).intValue();
    }

    private void ensureAtLeast(String sequence, int max) {
        if (max <= 0) {
            return;
        }
        mongo.upsert(Query.query(Criteria.where("_id").is(sequence)), new Update().max("seq", max), COUNTERS);
        org.bson.Document counter = mongo.findById(sequence, org.bson.Document.class, COUNTERS);
        int value = counter == null ? max : ((Number) counter.get("seq")).intValue();
        report.getCounters().put(sequence, value);
        log.info("counters.{} = {}", sequence, value);
    }
}
