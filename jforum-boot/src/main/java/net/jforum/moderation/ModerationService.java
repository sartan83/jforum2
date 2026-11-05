package net.jforum.moderation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.jforum.attachment.AttachmentService;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Forum;
import net.jforum.domain.LastPostInfo;
import net.jforum.domain.ModerationLog;
import net.jforum.domain.ModerationLogType;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicStatus;
import net.jforum.domain.User;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.ModerationLogRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.UserRepository;
import net.jforum.support.SequenceService;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Moderation operations ported from legacy ModerationHelper / admin ModerationAction / the DAO counter
 * updates they trigger. Every operation keeps the topic and forum counters (replies, totalTopics,
 * totalPosts, last post info) consistent and writes a {@link ModerationLog} entry when logging is enabled.
 * Permission checks are the caller's responsibility (see {@link ModerationController}).
 */
@Service
public class ModerationService {

    public static final String MODERATION_LOGGING_ENABLED = "moderation.logging.enabled";

    private final MongoTemplate mongo;
    private final TopicRepository topics;
    private final PostRepository posts;
    private final ForumRepository forums;
    private final UserRepository users;
    private final ModerationLogRepository logs;
    private final SequenceService sequences;
    private final AttachmentService attachments;
    private final ForumConfig config;

    public ModerationService(MongoTemplate mongo, TopicRepository topics, PostRepository posts, ForumRepository forums,
                             UserRepository users, ModerationLogRepository logs, SequenceService sequences,
                             AttachmentService attachments, ForumConfig config) {
        this.mongo = mongo;
        this.topics = topics;
        this.posts = posts;
        this.forums = forums;
        this.users = users;
        this.logs = logs;
        this.sequences = sequences;
        this.attachments = attachments;
        this.config = config;
    }

    /** Who performs a moderation action and why (legacy log_description / log_type form fields). */
    public record Moderator(Integer userId, String username, String reason) {
        public static Moderator of(User user, String reason) {
            return new Moderator(user.getId(), user.getUsername(), reason);
        }
    }

    // ---------------------------------------------------------------- lock / unlock

    public void lock(Collection<Integer> topicIds, Moderator moderator) {
        setStatus(topicIds, TopicStatus.LOCKED, ModerationLogType.LOCK, moderator);
    }

    public void unlock(Collection<Integer> topicIds, Moderator moderator) {
        setStatus(topicIds, TopicStatus.UNLOCKED, ModerationLogType.UNLOCK, moderator);
    }

    private void setStatus(Collection<Integer> topicIds, TopicStatus status, ModerationLogType type, Moderator moderator) {
        for (Topic topic : topics.findAllById(topicIds)) {
            topic.setStatus(status);
            topics.save(topic);
            log(moderator, type, topic, firstPost(topic));
        }
    }

    // ---------------------------------------------------------------- move

    /** Legacy ForumDAO.moveTopics: topics and their posts change forum, both forums' stats are refreshed. */
    public void move(Collection<Integer> topicIds, int fromForumId, int toForumId, Moderator moderator) {
        Forum to = forums.findById(toForumId).orElseThrow(() -> new IllegalArgumentException("Unknown forum " + toForumId));
        List<Integer> moved = new ArrayList<>();
        for (Topic topic : topics.findAllById(topicIds)) {
            if (!topic.getForumId().equals(fromForumId) || fromForumId == toForumId) {
                continue;
            }
            topic.setForumId(toForumId);
            topic.setMovedId(null);
            topic.setModerated(to.isModerated());
            topics.save(topic);
            mongo.updateMulti(Query.query(Criteria.where("topicId").is(topic.getId())),
                Update.update("forumId", toForumId), Post.class);
            moved.add(topic.getId());
            log(moderator, ModerationLogType.MOVE, topic, firstPost(topic));
        }
        if (!moved.isEmpty()) {
            refreshForum(fromForumId);
            refreshForum(toForumId);
        }
    }

    // ---------------------------------------------------------------- delete

    /** Legacy ModerationHelper.removeTopics: deletes topics with all their posts and attachments. */
    public void deleteTopics(Collection<Integer> topicIds, Moderator moderator) {
        Map<Integer, Boolean> touchedForums = new LinkedHashMap<>();
        for (Topic topic : topics.findAllById(topicIds)) {
            Post first = firstPost(topic);
            log(moderator, ModerationLogType.DELETE, topic, first);
            for (Post post : posts.findByTopicIdOrderByTimeAsc(topic.getId())) {
                removePostData(post);
            }
            posts.deleteByTopicId(topic.getId());
            mongo.remove(Query.query(Criteria.where("topicId").is(topic.getId())), "topic_watch");
            topics.delete(topic);
            touchedForums.put(topic.getForumId(), true);
        }
        touchedForums.keySet().forEach(this::refreshForum);
    }

    /** Legacy PostAction.delete: removes one post; the topic goes away too when it was its only post. */
    public void deletePost(int postId, Moderator moderator) {
        Post post = posts.findById(postId).orElse(null);
        if (post == null) {
            return;
        }
        Topic topic = topics.findById(post.getTopicId()).orElse(null);
        log(moderator, ModerationLogType.DELETE, topic, post);
        removePostData(post);
        posts.delete(post);
        if (topic != null) {
            if (posts.findByTopicIdOrderByTimeAsc(topic.getId()).isEmpty()) {
                topics.delete(topic);
            } else {
                refreshTopic(topic.getId());
            }
        }
        refreshForum(post.getForumId());
    }

    private void removePostData(Post post) {
        if (!post.isModerate() && post.getUserId() != null) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(post.getUserId()).and("totalPosts").gt(0)),
                new Update().inc("totalPosts", -1), User.class);
        }
        attachments.deleteByPost(post.getId());
    }

    // ---------------------------------------------------------------- moderation queue

    /** Posts waiting for approval (moderate=true) in the given forum, oldest first like legacy. */
    public List<Post> pendingPosts(int forumId) {
        return mongo.find(Query.query(Criteria.where("forumId").is(forumId).and("moderate").is(true))
            .with(Sort.by("topicId", "time")), Post.class);
    }

    /** Number of pending posts per forum id. */
    public Map<Integer, Long> pendingCountByForum() {
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (Post p : posts.findByModerateTrue()) {
            result.merge(p.getForumId(), 1L, Long::sum);
        }
        return result;
    }

    /** Legacy admin ModerationAction "aprove": the post becomes visible and every counter is updated. */
    public void approve(int postId, Moderator moderator) {
        Post post = posts.findById(postId).orElse(null);
        if (post == null || !post.isModerate()) {
            return;
        }
        post.setModerate(false);
        posts.save(post);
        if (post.getUserId() != null) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(post.getUserId())), new Update().inc("totalPosts", 1), User.class);
        }
        Topic topic = topics.findById(post.getTopicId()).orElse(null);
        if (topic != null) {
            topic.setModerated(false);
            topics.save(topic);
            refreshTopic(topic.getId());
        }
        refreshForum(post.getForumId());
        log(moderator, ModerationLogType.APPROVE, topic, post);
    }

    /** Legacy admin ModerationAction "reject": the pending post is deleted (and its topic, if left empty). */
    public void reject(int postId, Moderator moderator) {
        Post post = posts.findById(postId).orElse(null);
        if (post == null || !post.isModerate()) {
            return;
        }
        Topic topic = topics.findById(post.getTopicId()).orElse(null);
        log(moderator, ModerationLogType.REJECT, topic, post);
        attachments.deleteByPost(post.getId());
        posts.delete(post);
        if (topic != null) {
            if (posts.findByTopicIdOrderByTimeAsc(topic.getId()).isEmpty()) {
                topics.delete(topic);
            } else {
                refreshTopic(topic.getId());
            }
        }
        refreshForum(post.getForumId());
    }

    // ---------------------------------------------------------------- counters

    /** Recomputes replies, first/last post data of a topic from its approved posts. */
    public void refreshTopic(int topicId) {
        Topic topic = topics.findById(topicId).orElse(null);
        if (topic == null) {
            return;
        }
        List<Post> visible = mongo.find(Query.query(Criteria.where("topicId").is(topicId).and("moderate").is(false))
            .with(Sort.by("time", "_id")), Post.class);
        if (visible.isEmpty()) {
            topic.setReplies(0);
            topics.save(topic);
            return;
        }
        Post first = visible.get(0);
        Post last = visible.get(visible.size() - 1);
        topic.setReplies(visible.size() - 1);
        topic.setFirstPostId(first.getId());
        topic.setLastPostId(last.getId());
        topic.setLastPostTime(last.getTime());
        topic.setLastPostUserId(last.getUserId());
        topic.setLastPostUsername(last.getUsername());
        topics.save(topic);
    }

    /** Legacy ForumDAO.getForumStats / setLastPost: totals and last post of a forum. */
    public void refreshForum(int forumId) {
        Forum forum = forums.findById(forumId).orElse(null);
        if (forum == null) {
            return;
        }
        forum.setTotalTopics((int) mongo.count(Query.query(Criteria.where("forumId").is(forumId)), Topic.class));
        forum.setTotalPosts((int) posts.countByForumIdAndModerateFalse(forumId));
        Post last = mongo.findOne(Query.query(Criteria.where("forumId").is(forumId).and("moderate").is(false))
            .with(Sort.by(Sort.Direction.DESC, "time", "_id")).limit(1), Post.class);
        if (last == null) {
            forum.setLastPostId(null);
            forum.setLastPostInfo(null);
        } else {
            forum.setLastPostId(last.getId());
            LastPostInfo info = new LastPostInfo();
            info.setPostId(last.getId());
            info.setTopicId(last.getTopicId());
            info.setUserId(last.getUserId());
            info.setUsername(last.getUsername());
            info.setPostTime(last.getTime());
            topics.findById(last.getTopicId()).ifPresent(t -> info.setTopicReplies(t.getReplies()));
            forum.setLastPostInfo(info);
        }
        forums.save(forum);
    }

    // ---------------------------------------------------------------- log

    public boolean isLoggingEnabled() {
        String v = config.get(MODERATION_LOGGING_ENABLED, "true");
        return !"false".equalsIgnoreCase(v.trim());
    }

    /** Legacy ModerationHelper.logActivity. */
    public void log(Moderator moderator, ModerationLogType type, Topic topic, Post post) {
        if (moderator == null || !isLoggingEnabled()) {
            return;
        }
        ModerationLog entry = new ModerationLog();
        entry.setId(sequences.next("moderation_log"));
        entry.setUserId(moderator.userId());
        entry.setUsername(moderator.username());
        entry.setDescription(moderator.reason());
        entry.setDate(Instant.now());
        entry.setType(type);
        if (topic != null) {
            entry.setTopicId(topic.getId());
        }
        if (post != null) {
            entry.setPostId(post.getId());
            entry.setTopicId(post.getTopicId());
            entry.setPostUserId(post.getUserId());
            entry.setPostUsername(post.getUsername());
            entry.setOriginalMessage(post.getText());
        } else if (topic != null) {
            entry.setPostUserId(topic.getUserId());
            entry.setOriginalMessage(topic.getTitle());
        }
        logs.save(entry);
    }

    private Post firstPost(Topic topic) {
        if (topic.getFirstPostId() != null) {
            Post p = posts.findById(topic.getFirstPostId()).orElse(null);
            if (p != null) {
                return p;
            }
        }
        List<Post> all = posts.findByTopicIdOrderByTimeAsc(topic.getId());
        return all.isEmpty() ? null : all.get(0);
    }
}
