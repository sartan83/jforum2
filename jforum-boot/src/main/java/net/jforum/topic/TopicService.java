package net.jforum.topic;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Forum;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicType;
import net.jforum.domain.TopicWatch;
import net.jforum.domain.User;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.TopicWatchRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators.Switch.CaseOperator;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/** Topic listings (legacy TopicDAO selectAllByForumByLimit / recent / hottest / new messages) and topic watches. */
@Service
public class TopicService {

    private final MongoTemplate mongo;
    private final ForumRepository forums;
    private final TopicWatchRepository watches;
    private final ForumConfig config;

    public TopicService(MongoTemplate mongo, ForumRepository forums, TopicWatchRepository watches, ForumConfig config) {
        this.mongo = mongo;
        this.forums = forums;
        this.watches = watches;
        this.config = config;
    }

    private static Criteria visible() {
        return Criteria.where("moderated").ne(true);
    }

    /** Topics of a forum: announcements, then sticky, then normal, newest last post first. */
    public List<Topic> forumTopics(int forumId, int start, int perPage) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("forumId").is(forumId).andOperator(visible())),
            Aggregation.addFields().addFieldWithValue("typeRank", ConditionalOperators.switchCases(
                CaseOperator.when(ComparisonOperators.valueOf("type").equalToValue(TopicType.ANNOUNCE.name())).then(2),
                CaseOperator.when(ComparisonOperators.valueOf("type").equalToValue(TopicType.STICKY.name())).then(1))
                .defaultTo(0)).build(),
            Aggregation.sort(Sort.by(Sort.Direction.DESC, "typeRank", "lastPostId")),
            Aggregation.skip((long) start),
            Aggregation.limit(perPage));
        return mongo.aggregate(agg, Topic.class, Topic.class).getMappedResults();
    }

    public long countForumTopics(int forumId) {
        return mongo.count(new Query(Criteria.where("forumId").is(forumId).andOperator(visible())), Topic.class);
    }

    public List<Topic> recentTopics(Collection<Integer> forumIds, int limit) {
        Query q = new Query(Criteria.where("forumId").in(forumIds).andOperator(visible()))
            .with(Sort.by(Sort.Direction.DESC, "lastPostId")).limit(limit);
        return mongo.find(q, Topic.class);
    }

    public List<Topic> hottestTopics(Collection<Integer> forumIds, int limit) {
        Query q = new Query(Criteria.where("forumId").in(forumIds).andOperator(visible()))
            .with(Sort.by(Sort.Direction.DESC, "views", "lastPostId")).limit(limit);
        return mongo.find(q, Topic.class);
    }

    private Criteria newMessagesCriteria(Collection<Integer> forumIds, Instant since) {
        return Criteria.where("forumId").in(forumIds).and("lastPostTime").gt(since).andOperator(visible());
    }

    public List<Topic> newMessages(Collection<Integer> forumIds, Instant since, int start, int perPage) {
        Query q = new Query(newMessagesCriteria(forumIds, since))
            .with(Sort.by(Sort.Direction.DESC, "lastPostId")).skip(start).limit(perPage);
        return mongo.find(q, Topic.class);
    }

    public long countNewMessages(Collection<Integer> forumIds, Instant since) {
        return mongo.count(new Query(newMessagesCriteria(forumIds, since)), Topic.class);
    }

    public void incrementViews(int topicId) {
        mongo.updateFirst(new Query(Criteria.where("_id").is(topicId)), new Update().inc("views", 1), Topic.class);
    }

    /** Legacy TopicsCommon.prepareTopics. */
    public List<TopicView> prepare(List<Topic> topics, HttpSession session, User user, boolean logged) {
        Map<Integer, Forum> byId = forums.findAllById(topics.stream().map(Topic::getForumId).distinct().toList())
            .stream().collect(Collectors.toMap(Forum::getId, Function.identity()));
        int hotBegin = config.getInt("hot.topic.begin", 20);
        int postsPerPage = config.postsPerPage();
        return topics.stream()
            .map(t -> new TopicView(t, byId.get(t.getForumId()), ReadTracker.isRead(session, user, logged, t),
                t.getReplies() >= hotBegin, postsPerPage))
            .toList();
    }

    // Topic watches (legacy TopicDAO subscribe/removeSubscription/isUserSubscribed/updateReadStatus)

    public boolean isWatching(int topicId, int userId) {
        return watches.findByTopicIdAndUserId(topicId, userId).isPresent();
    }

    public void watch(int topicId, int userId) {
        if (!isWatching(topicId, userId)) {
            TopicWatch w = new TopicWatch();
            w.setTopicId(topicId);
            w.setUserId(userId);
            w.setRead(true);
            watches.save(w);
        }
    }

    public void unwatch(int topicId, int userId) {
        watches.deleteByTopicIdAndUserId(topicId, userId);
    }

    public void markWatchRead(int topicId, int userId) {
        watches.findByTopicIdAndUserId(topicId, userId).filter(w -> !w.isRead()).ifPresent(w -> {
            w.setRead(true);
            watches.save(w);
        });
    }
}
