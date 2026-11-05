package net.jforum.migration;

import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Final verification: compares, for every migrated table, the number of distinct source rows (the ones that can
 * become documents or embedded elements) with what is now stored in MongoDB.
 */
class MigrationVerifier {

    private static final Logger log = LoggerFactory.getLogger(MigrationVerifier.class);

    private final MongoTemplate mongo;
    private final LegacySource legacy;
    private final MigrationReport report;

    MigrationVerifier(MongoTemplate mongo, LegacySource legacy, MigrationReport report) {
        this.mongo = mongo;
        this.legacy = legacy;
        this.report = report;
    }

    void verify() {
        collection("jforum_users", "users", "SELECT COUNT(DISTINCT user_id) FROM jforum_users");
        collection("jforum_groups", "groups", "SELECT COUNT(DISTINCT group_id) FROM jforum_groups");
        embedded("jforum_user_groups", "users.groupIds",
            "SELECT COUNT(*) FROM (SELECT DISTINCT ug.user_id, ug.group_id FROM jforum_user_groups ug "
                + "JOIN jforum_users u ON u.user_id = ug.user_id) x",
            sizeSum("users", null, "$groupIds"), "jforum_users");
        embedded("jforum_roles", "groups.roles",
            "SELECT COUNT(*) FROM (SELECT DISTINCT r.group_id, LTRIM(RTRIM(r.name)) AS name FROM jforum_roles r "
                + "JOIN jforum_groups g ON g.group_id = r.group_id WHERE LTRIM(RTRIM(r.name)) <> '') x",
            sizeSum("groups", null, "$roles"), "jforum_groups");
        embedded("jforum_role_values", "groups.roles.values",
            "SELECT COUNT(*) FROM (SELECT DISTINCT r.group_id, LTRIM(RTRIM(r.name)) AS name, "
                + "LTRIM(RTRIM(rv.role_value)) AS v FROM jforum_roles r "
                + "JOIN jforum_groups g ON g.group_id = r.group_id JOIN jforum_role_values rv ON rv.role_id = r.role_id "
                + "WHERE LTRIM(RTRIM(r.name)) <> '' AND LTRIM(RTRIM(rv.role_value)) <> '') x",
            sizeSum("groups", "$roles", "$roles.values"), "jforum_roles");
        collection("jforum_categories", "categories", "SELECT COUNT(DISTINCT categories_id) FROM jforum_categories");
        collection("jforum_forums", "forums", "SELECT COUNT(DISTINCT forum_id) FROM jforum_forums");
        collection("jforum_topics", "topics", "SELECT COUNT(DISTINCT topic_id) FROM jforum_topics");
        embedded("jforum_vote_desc", "topics.poll",
            "SELECT COUNT(DISTINCT d.topic_id) FROM jforum_vote_desc d JOIN jforum_topics t ON t.topic_id = d.topic_id",
            () -> mongo.count(Query.query(Criteria.where("poll").ne(null)), "topics"), "jforum_topics");
        embedded("jforum_vote_results", "topics.poll.options",
            "SELECT COUNT(*) FROM jforum_vote_results r JOIN jforum_vote_desc d ON d.vote_id = r.vote_id "
                + "JOIN jforum_topics t ON t.topic_id = d.topic_id "
                + "WHERE d.vote_id = (SELECT MAX(d2.vote_id) FROM jforum_vote_desc d2 WHERE d2.topic_id = d.topic_id)",
            sizeSum("topics", null, "$poll.options"), "jforum_vote_desc");
        embedded("jforum_vote_voters", "topics.poll.voters",
            "SELECT COUNT(*) FROM jforum_vote_voters r JOIN jforum_vote_desc d ON d.vote_id = r.vote_id "
                + "JOIN jforum_topics t ON t.topic_id = d.topic_id "
                + "WHERE d.vote_id = (SELECT MAX(d2.vote_id) FROM jforum_vote_desc d2 WHERE d2.topic_id = d.topic_id)",
            sizeSum("topics", null, "$poll.voters"), "jforum_vote_desc");
        collection("jforum_posts", "posts", "SELECT COUNT(DISTINCT post_id) FROM jforum_posts");
        embedded("jforum_posts_text", "posts.text",
            "SELECT COUNT(DISTINCT p.post_id) FROM jforum_posts p JOIN jforum_posts_text pt ON pt.post_id = p.post_id "
                + "WHERE pt.post_text IS NOT NULL",
            () -> mongo.count(Query.query(Criteria.where("text").ne(null)), "posts"), "jforum_posts");
        collection("jforum_privmsgs", "private_messages", "SELECT COUNT(DISTINCT privmsgs_id) FROM jforum_privmsgs");
        embedded("jforum_privmsgs_text", "private_messages.text",
            "SELECT COUNT(DISTINCT p.privmsgs_id) FROM jforum_privmsgs p "
                + "JOIN jforum_privmsgs_text t ON t.privmsgs_id = p.privmsgs_id WHERE t.privmsgs_text IS NOT NULL",
            () -> mongo.count(Query.query(Criteria.where("text").ne(null)), "private_messages"), "jforum_privmsgs");
        collection("jforum_attach", "attachments", "SELECT COUNT(DISTINCT attach_id) FROM jforum_attach");
        if (legacy.exists("jforum_attach") && report.getAttachmentFilesStored() + report.getAttachmentFilesMissing() > 0) {
            check("attachment files (upload dir)", "attachments.gridFsId", report.getAttachmentFilesStored(),
                mongo.count(Query.query(Criteria.where("gridFsId").ne(null)), "attachments"));
        }
        collection("jforum_karma", "karma", "SELECT COUNT(DISTINCT karma_id) FROM jforum_karma");
        collection("jforum_bookmarks", "bookmarks", "SELECT COUNT(DISTINCT bookmark_id) FROM jforum_bookmarks");
        collection("jforum_topics_watch", "topic_watch",
            "SELECT COUNT(*) FROM (SELECT DISTINCT topic_id, user_id FROM jforum_topics_watch) x");
        collection("jforum_forums_watch", "forum_watch",
            "SELECT COUNT(*) FROM (SELECT DISTINCT forum_id, user_id FROM jforum_forums_watch) x");
        collection("jforum_ranks", "rankings", "SELECT COUNT(DISTINCT rank_id) FROM jforum_ranks");
        collection("jforum_smilies", "smilies", "SELECT COUNT(DISTINCT smilie_id) FROM jforum_smilies");
        collection("jforum_banlist", "banlist", "SELECT COUNT(DISTINCT banlist_id) FROM jforum_banlist");
        collection("jforum_config", "config",
            "SELECT COUNT(DISTINCT LTRIM(RTRIM(config_name))) FROM jforum_config WHERE LTRIM(RTRIM(config_name)) <> ''");
        collection("jforum_moderation_log", "moderation_log", "SELECT COUNT(DISTINCT log_id) FROM jforum_moderation_log");
        collection("jforum_extension_groups", "extension_groups",
            "SELECT COUNT(DISTINCT extension_group_id) FROM jforum_extension_groups");
        collection("jforum_extensions", "extensions", "SELECT COUNT(DISTINCT extension_id) FROM jforum_extensions");
        collection("jforum_quota_limit", "quota_limits", "SELECT COUNT(DISTINCT quota_limit_id) FROM jforum_quota_limit");
        embedded("jforum_attach_quota", "quota_limits.groupIds",
            "SELECT COUNT(*) FROM (SELECT DISTINCT aq.quota_limit_id, aq.group_id FROM jforum_attach_quota aq "
                + "JOIN jforum_quota_limit q ON q.quota_limit_id = aq.quota_limit_id) x",
            sizeSum("quota_limits", null, "$groupIds"), "jforum_quota_limit");
        collection("jforum_banner", "banners", "SELECT COUNT(DISTINCT banner_id) FROM jforum_banner");
        collection("jforum_api", "api_keys", "SELECT COUNT(DISTINCT api_id) FROM jforum_api");

        for (MigrationReport.Check c : report.getChecks()) {
            if (c.ok()) {
                log.info("verify {} -> {}: {} = {} OK", c.source(), c.target(), c.expected(), c.actual());
            } else {
                log.error("verify {} -> {}: expected {} but found {} MISMATCH", c.source(), c.target(), c.expected(),
                    c.actual());
            }
        }
    }

    private void collection(String table, String collection, String sql) {
        if (legacy.exists(table)) {
            check(table, collection, legacy.count(sql), mongo.getCollection(collection).countDocuments());
        }
    }

    private void embedded(String table, String target, String sql, java.util.function.LongSupplier actual,
                          String... requiredTables) {
        if (!legacy.exists(table)) {
            return;
        }
        for (String required : requiredTables) {
            if (!legacy.exists(required)) {
                return;
            }
        }
        check(table, target, legacy.count(sql), actual.getAsLong());
    }

    private void check(String source, String target, long expected, long actual) {
        report.getChecks().add(new MigrationReport.Check(source, target, expected, actual));
    }

    /** Sum of the sizes of an array field over a collection, optionally after unwinding an outer array. */
    private java.util.function.LongSupplier sizeSum(String collection, String unwind, String array) {
        return () -> {
            List<Document> pipeline = new java.util.ArrayList<>();
            if (unwind != null) {
                pipeline.add(new Document("$unwind", unwind));
            }
            pipeline.add(new Document("$group", new Document("_id", null).append("n",
                new Document("$sum", new Document("$size", new Document("$ifNull", List.of(array, List.of())))))));
            Document result = mongo.getCollection(collection).aggregate(pipeline).first();
            return result == null ? 0 : ((Number) result.get("n")).longValue();
        };
    }
}
