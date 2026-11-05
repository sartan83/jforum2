package net.jforum.post;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Forum;
import net.jforum.domain.LastPostInfo;
import net.jforum.domain.ModerationLog;
import net.jforum.domain.ModerationLogType;
import net.jforum.domain.Poll;
import net.jforum.domain.PollOption;
import net.jforum.domain.PollVoter;
import net.jforum.domain.Post;
import net.jforum.domain.Ranking;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicType;
import net.jforum.domain.User;
import net.jforum.format.PostFormatter;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.ModerationLogRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.RankingRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.TopicWatchRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.PermissionControl;
import net.jforum.security.SecurityConstants;
import net.jforum.support.SequenceService;
import net.jforum.topic.TopicService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/** Post persistence and counters (legacy PostAction insertSave/editSave/delete/vote, TopicsCommon.updateBoardStatus). */
@Service
public class PostService {

    private final PostRepository posts;
    private final TopicRepository topics;
    private final ForumRepository forums;
    private final UserRepository users;
    private final RankingRepository rankings;
    private final TopicWatchRepository topicWatches;
    private final ModerationLogRepository moderationLogs;
    private final SequenceService sequences;
    private final MongoTemplate mongo;
    private final PostFormatter formatter;
    private final ForumConfig config;
    private final TopicService topicService;
    private final NotificationService notifications;

    public PostService(PostRepository posts, TopicRepository topics, ForumRepository forums, UserRepository users,
                       RankingRepository rankings, TopicWatchRepository topicWatches, ModerationLogRepository moderationLogs,
                       SequenceService sequences, MongoTemplate mongo, PostFormatter formatter, ForumConfig config,
                       TopicService topicService, NotificationService notifications) {
        this.posts = posts;
        this.topics = topics;
        this.forums = forums;
        this.users = users;
        this.rankings = rankings;
        this.topicWatches = topicWatches;
        this.moderationLogs = moderationLogs;
        this.sequences = sequences;
        this.mongo = mongo;
        this.formatter = formatter;
        this.config = config;
        this.topicService = topicService;
        this.notifications = notifications;
    }

    // ---- Reading ----

    /** Legacy PostDAO.selectAllByTopicByLimit: approved posts plus the viewer's own posts waiting moderation. */
    private Criteria topicPostsCriteria(int topicId, int viewerId) {
        return Criteria.where("topicId").is(topicId)
            .orOperator(Criteria.where("moderate").ne(true), Criteria.where("userId").is(viewerId));
    }

    public List<Post> topicPosts(int topicId, int viewerId, int start, int perPage) {
        Query q = new Query(topicPostsCriteria(topicId, viewerId)).with(Sort.by("time", "_id")).skip(start).limit(perPage);
        return mongo.find(q, Post.class);
    }

    public long countTopicPosts(int topicId, int viewerId) {
        return mongo.count(new Query(topicPostsCriteria(topicId, viewerId)), Post.class);
    }

    /** Number of approved posts before the given one, used by preList to find the page of a post. */
    public long countPostsBefore(int topicId, int postId) {
        return posts.countByTopicIdAndModerateFalseAndIdLessThan(topicId, postId);
    }

    public String format(Post p) {
        return formatter.format(p.getText(), p.isHtmlEnabled(), p.isBbCodeEnabled(), p.isSmiliesEnabled());
    }

    public List<PostView> views(List<Post> list, int viewerId, boolean logged, PermissionControl pc, String contextPath) {
        Map<Integer, User> authors = users.findAllById(list.stream().map(Post::getUserId).filter(Objects::nonNull).distinct().toList())
            .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<Ranking> ranks = rankings.findAllByOrderByMinAsc();
        boolean moderatorEdit = pc.canAccess(SecurityConstants.PERM_MODERATION_POST_EDIT);
        List<PostView> result = new ArrayList<>();
        for (Post p : list) {
            User u = authors.get(p.getUserId());
            boolean canEdit = logged && (moderatorEdit || Objects.equals(p.getUserId(), viewerId));
            result.add(new PostView(p, format(p), author(u, p, ranks, contextPath), canEdit));
        }
        return result;
    }

    private AuthorView author(User u, Post p, List<Ranking> ranks, String contextPath) {
        if (u == null) {
            return new AuthorView(null, null, null, null, null, true);
        }
        Ranking rank = null;
        if (u.getRankId() != null && u.getRankId() > 0) {
            rank = ranks.stream().filter(r -> r.isSpecial() && r.getId().equals(u.getRankId())).findFirst().orElse(null);
        }
        if (rank == null) {
            for (Ranking r : ranks) {
                if (!r.isSpecial() && r.getMin() <= u.getTotalPosts()) {
                    rank = r;
                }
            }
        }
        String avatar = null;
        String avatarUrl = u.isAvatarEnabled() ? u.getAvatarUrl() : null;
        if (avatarUrl != null) {
            avatar = u.isExternalAvatar() ? avatarUrl : contextPath + avatarUrl;
        }
        String signature = null;
        if (p.isSignatureEnabled() && u.getSignature() != null && !u.getSignature().isBlank()) {
            signature = formatter.format(u.getSignature(), false, true, true);
        }
        return new AuthorView(u, rank == null ? null : rank.getTitle(), rank == null ? null : rank.getImage(), avatar, signature,
            u.getId() == 1);
    }

    // ---- Writing ----

    /** Legacy insertSave: creates the topic (when topic is null) and the post, then updates the board status. */
    public Post insert(PostCommand c, Forum forum, Topic topic, User user, boolean logged, PermissionControl pc, String ip) {
        boolean newTopic = topic == null;
        Instant now = Instant.now();
        boolean moderate = forum.isModerated()
            && !pc.canAccess(SecurityConstants.PERM_MODERATION)
            && !pc.canAccess(SecurityConstants.PERM_ADMINISTRATION);
        if (newTopic) {
            topic = new Topic();
            topic.setId((int) sequences.next("topics"));
            topic.setForumId(forum.getId());
            topic.setTitle(c.getSubject());
            topic.setUserId(user.getId());
            topic.setUsername(user.getUsername());
            topic.setTime(now);
            topic.setModerated(moderate);
            topic.setType(pc.canAccess(SecurityConstants.PERM_CREATE_STICKY_ANNOUNCEMENT_TOPICS) ? c.getTopicType() : TopicType.NORMAL);
            if (pc.canAccess(SecurityConstants.PERM_CREATE_POLL) && c.hasPoll()) {
                Poll poll = c.toPoll();
                poll.setId((int) sequences.next("polls"));
                poll.setStartTime(now);
                topic.setPoll(poll);
            }
        } else if (pc.canAccess(SecurityConstants.PERM_REPLY_WITHOUT_MODERATION, topic.getForumId())) {
            moderate = false;
        }

        Post post = new Post();
        post.setId((int) sequences.next("posts"));
        post.setTopicId(topic.getId());
        post.setForumId(forum.getId());
        post.setUserId(user.getId());
        post.setUsername(user.getUsername());
        post.setTime(now);
        post.setUserIp(ip);
        applyForm(post, c, forum.getId(), pc);
        post.setModerate(moderate);
        if (newTopic) {
            topic.setFirstPostId(post.getId());
            topic.setLastPostId(post.getId());
            topic.setLastPostTime(now);
            topic.setLastPostUserId(user.getId());
            topic.setLastPostUsername(user.getUsername());
            topics.save(topic);
        }
        posts.save(post);

        if (logged && c.isNotify()) {
            topicService.watch(topic.getId(), user.getId());
        }
        if (!moderate) {
            updateBoardStatus(topic, newTopic);
            mongo.updateFirst(new Query(Criteria.where("_id").is(user.getId())), new Update().inc("totalPosts", 1), User.class);
            if (newTopic) {
                notifications.notifyForumWatchers(forum, topic, post);
            } else {
                notifications.notifyTopicWatchers(topic, post);
            }
        }
        return post;
    }

    private void applyForm(Post post, PostCommand c, int forumId, PermissionControl pc) {
        post.setSubject(c.getSubject());
        post.setText(c.getMessage());
        post.setHtmlEnabled(pc.canAccess(SecurityConstants.PERM_HTML_DISABLED, forumId) && !c.isDisableHtml());
        post.setBbCodeEnabled(!c.isDisableBbCode());
        post.setSmiliesEnabled(!c.isDisableSmilies());
        post.setSignatureEnabled(c.isAttachSig());
    }

    /** Legacy TopicsCommon.updateBoardStatus (views incremented too, like legacy). */
    private void updateBoardStatus(Topic topic, boolean newTopic) {
        refreshTopic(topic);
        if (!newTopic) {
            topicService.incrementViews(topic.getId());
        }
        refreshForum(topic.getForumId());
    }

    /** Recomputes replies, first and last post of a topic from the approved posts. */
    public void refreshTopic(Topic topic) {
        long approved = posts.countByTopicIdAndModerateFalse(topic.getId());
        Topic fresh = topics.findById(topic.getId()).orElse(topic);
        fresh.setReplies((int) Math.max(0, approved - 1));
        posts.findFirstByTopicIdOrderByIdAsc(topic.getId()).ifPresent(p -> fresh.setFirstPostId(p.getId()));
        posts.findFirstByTopicIdAndModerateFalseOrderByIdDesc(topic.getId()).ifPresent(p -> {
            fresh.setLastPostId(p.getId());
            fresh.setLastPostTime(p.getTime());
            fresh.setLastPostUserId(p.getUserId());
            fresh.setLastPostUsername(p.getUsername());
        });
        if (approved > 0) {
            fresh.setModerated(false);
        }
        topics.save(fresh);
        topic.setReplies(fresh.getReplies());
        topic.setLastPostId(fresh.getLastPostId());
        topic.setLastPostTime(fresh.getLastPostTime());
        topic.setFirstPostId(fresh.getFirstPostId());
    }

    /** Recomputes totalTopics, totalPosts and the last post info of a forum. */
    public void refreshForum(int forumId) {
        forums.findById(forumId).ifPresent(f -> {
            f.setTotalTopics((int) topics.countByForumIdAndModeratedFalse(forumId));
            f.setTotalPosts((int) posts.countByForumIdAndModerateFalse(forumId));
            Optional<Post> last = posts.findFirstByForumIdAndModerateFalseOrderByIdDesc(forumId);
            if (last.isPresent()) {
                Post p = last.get();
                LastPostInfo info = new LastPostInfo();
                info.setPostId(p.getId());
                info.setTopicId(p.getTopicId());
                info.setUserId(p.getUserId());
                info.setUsername(p.getUsername());
                info.setPostTime(p.getTime());
                info.setTopicReplies(topics.findById(p.getTopicId()).map(Topic::getReplies).orElse(0));
                f.setLastPostId(p.getId());
                f.setLastPostInfo(info);
            } else {
                f.setLastPostId(null);
                f.setLastPostInfo(null);
            }
            forums.save(f);
        });
    }

    /** Legacy editSave. */
    public void edit(Post post, Topic topic, PostCommand c, User editor, PermissionControl pc) {
        String original = post.getText();
        applyForm(post, c, post.getForumId(), pc);
        post.setEditTime(Instant.now());
        post.setEditCount(post.getEditCount() + 1);
        posts.save(post);

        if (Objects.equals(topic.getFirstPostId(), post.getId())) {
            if (!c.getSubject().isBlank()) {
                topic.setTitle(c.getSubject());
            }
            if (pc.canAccess(SecurityConstants.PERM_CREATE_STICKY_ANNOUNCEMENT_TOPICS)) {
                topic.setType(c.getTopicType());
            }
            if (pc.canAccess(SecurityConstants.PERM_CREATE_POLL)) {
                topic.setPoll(mergePoll(topic.getPoll(), c));
            }
            topics.save(topic);
        }
        if (c.isNotify()) {
            topicService.watch(topic.getId(), editor.getId());
        } else {
            topicService.unwatch(topic.getId(), editor.getId());
        }
        if (!Objects.equals(post.getUserId(), editor.getId())) {
            log(ModerationLogType.EDIT, editor, post, original);
        }
    }

    private Poll mergePoll(Poll existing, PostCommand c) {
        Poll edited = c.toPoll();
        if (edited == null) {
            return null;
        }
        if (existing == null) {
            edited.setId((int) sequences.next("polls"));
            edited.setStartTime(Instant.now());
            return edited;
        }
        existing.setLabel(edited.getLabel());
        existing.setLength(edited.getLength());
        List<PollOption> merged = new ArrayList<>();
        int nextId = existing.getOptions().stream().mapToInt(PollOption::getId).max().orElse(0) + 1;
        for (PollOption o : edited.getOptions()) {
            PollOption keep = existing.getOptions().stream().filter(e -> e.getText().equals(o.getText())).findFirst().orElse(null);
            if (keep == null) {
                o.setId(nextId++);
                merged.add(o);
            } else {
                merged.add(keep);
            }
        }
        existing.setOptions(merged);
        return existing;
    }

    /** Legacy PostAction.delete. Returns the topic when it still exists, empty when it was removed. */
    public Optional<Topic> delete(Post post, User moderator) {
        posts.delete(post);
        if (!post.isModerate()) {
            mongo.updateFirst(new Query(Criteria.where("_id").is(post.getUserId()).and("totalPosts").gt(0)),
                new Update().inc("totalPosts", -1), User.class);
        }
        log(ModerationLogType.DELETE, moderator, post, post.getText());
        Optional<Topic> topic = topics.findById(post.getTopicId());
        if (topic.isEmpty()) {
            refreshForum(post.getForumId());
            return Optional.empty();
        }
        if (posts.countByTopicId(post.getTopicId()) == 0) {
            topics.delete(topic.get());
            topicWatches.deleteByTopicId(post.getTopicId());
            refreshForum(post.getForumId());
            return Optional.empty();
        }
        refreshTopic(topic.get());
        refreshForum(post.getForumId());
        return topic;
    }

    private void log(ModerationLogType type, User moderator, Post post, String original) {
        if (!config.getBool("moderation.logging.enabled")) {
            return;
        }
        ModerationLog l = new ModerationLog();
        l.setId((int) sequences.next("moderationLogs"));
        l.setUserId(moderator.getId());
        l.setUsername(moderator.getUsername());
        l.setDescription(type.name() + " post #" + post.getId());
        l.setOriginalMessage(original);
        l.setDate(Instant.now());
        l.setType(type);
        l.setPostId(post.getId());
        l.setTopicId(post.getTopicId());
        l.setPostUserId(post.getUserId());
        l.setPostUsername(post.getUsername());
        moderationLogs.save(l);
    }

    // ---- Polls ----

    public static boolean isPollOpen(Poll poll) {
        if (poll == null) {
            return false;
        }
        return poll.getLength() <= 0 || poll.getStartTime() == null
            || poll.getStartTime().plus(Duration.ofDays(poll.getLength())).isAfter(Instant.now());
    }

    public static boolean hasVoted(Poll poll, int userId) {
        return poll.getVoters().stream().anyMatch(v -> Objects.equals(v.getUserId(), userId));
    }

    /** Legacy PostAction.vote: records the vote if the option exists and the user has not voted yet. */
    public boolean vote(Topic topic, int optionId, int userId, String ip) {
        Poll poll = topic.getPoll();
        if (poll == null || !isPollOpen(poll) || hasVoted(poll, userId)
            || poll.getOptions().stream().noneMatch(o -> o.getId() == optionId)) {
            return false;
        }
        PollVoter voter = new PollVoter();
        voter.setUserId(userId);
        voter.setIp(ip);
        poll.getOptions().stream().filter(o -> o.getId() == optionId).findFirst().ifPresent(o -> o.setVoteCount(o.getVoteCount() + 1));
        poll.getVoters().add(voter);
        Query q = new Query(Criteria.where("_id").is(topic.getId()).and("poll.voters.userId").ne(userId));
        return mongo.updateFirst(q, new Update().set("poll", poll), Topic.class).getModifiedCount() > 0;
    }

    public static int totalVotes(Poll poll) {
        return poll == null ? 0 : poll.getOptions().stream().mapToInt(PollOption::getVoteCount).sum();
    }

    /** Preview HTML using the same flags insertSave/editSave would store. */
    public String previewHtml(PostCommand c, int forumId, PermissionControl pc) {
        Post p = new Post();
        applyForm(p, c, forumId, pc);
        return format(p);
    }

    public Collection<Integer> ids(List<Forum> list) {
        return list.stream().map(Forum::getId).toList();
    }
}
