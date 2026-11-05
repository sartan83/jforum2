package net.jforum.search;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.jforum.domain.Forum;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.format.PostFormatter;
import net.jforum.forum.ForumService;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.security.PermissionControl;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

/**
 * MongoDB text search replacing legacy Lucene search (SearchAction / LuceneSearch). Posts are matched on
 * subject+text, topics on title (a title hit returns the topic's first post). Only approved posts in forums
 * the user can read are returned.
 */
@Service
public class SearchService {

    /** Upper bound of keyword hits considered for one query (ranking/merging is done on this window). */
    static final int MAX_HITS = 2000;

    private final MongoTemplate mongo;
    private final ForumService forumService;
    private final TopicRepository topics;
    private final PostRepository posts;
    private final PostFormatter formatter;

    public SearchService(MongoTemplate mongo, ForumService forumService, TopicRepository topics, PostRepository posts,
                         PostFormatter formatter) {
        this.mongo = mongo;
        this.forumService = forumService;
        this.topics = topics;
        this.posts = posts;
        this.formatter = formatter;
    }

    public record Result(List<SearchHit> hits, long total) {
        public List<SearchHit> getHits() {
            return hits;
        }

        public long getTotal() {
            return total;
        }
    }

    public Result search(SearchArgs args, PermissionControl pc, int start, int perPage) {
        Map<Integer, Forum> visible = forumService.visibleForums(pc).stream()
            .collect(Collectors.toMap(Forum::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<Integer> forumIds = new ArrayList<>(visible.keySet());
        if (args.getForumId() != null) {
            forumIds.retainAll(List.of(args.getForumId()));
        }
        if (forumIds.isEmpty()) {
            return new Result(List.of(), 0);
        }
        Criteria filter = postFilter(args, forumIds);
        String[] terms = args.terms();
        List<Post> page;
        long total;
        if (terms.length == 0) {
            Query q = new Query(filter).with(Sort.by(args.isAscending() ? Sort.Direction.ASC : Sort.Direction.DESC, "time", "_id"));
            total = mongo.count(q, Post.class);
            page = mongo.find(q.skip(start).limit(perPage), Post.class);
        } else {
            List<Hit> hits = keywordHits(args, terms, filter, forumIds);
            total = hits.size();
            List<Integer> ids = hits.stream().skip(start).limit(perPage).map(Hit::id).toList();
            Map<Integer, Post> byId = new HashMap<>();
            posts.findAllById(ids).forEach(p -> byId.put(p.getId(), p));
            page = ids.stream().map(byId::get).filter(p -> p != null).toList();
        }
        Map<Integer, Topic> topicById = new HashMap<>();
        topics.findAllById(page.stream().map(Post::getTopicId).distinct().toList()).forEach(t -> topicById.put(t.getId(), t));
        List<SearchHit> result = page.stream().map(p -> new SearchHit(p, topicById.get(p.getTopicId()), visible.get(p.getForumId()),
            formatter.format(p.getText(), p.isHtmlEnabled(), p.isBbCodeEnabled(), p.isSmiliesEnabled()))).toList();
        return new Result(result, total);
    }

    private record Hit(int id, double score, Instant time) {
    }

    private List<Hit> keywordHits(SearchArgs args, String[] terms, Criteria filter, List<Integer> forumIds) {
        Map<Integer, Hit> merged = new LinkedHashMap<>();
        Query postQuery = TextQuery.queryText(textCriteria(args, terms)).includeScore("score").sortByScore()
            .addCriteria(filter).limit(MAX_HITS);
        postQuery.fields().include("_id", "time");
        for (Document d : mongo.find(postQuery, Document.class, mongo.getCollectionName(Post.class))) {
            merged.put(d.getInteger("_id"), new Hit(d.getInteger("_id"), number(d.get("score")), toInstant(d.get("time"))));
        }
        Query topicQuery = TextQuery.queryText(textCriteria(args, terms)).includeScore("score").sortByScore()
            .addCriteria(Criteria.where("forumId").in(forumIds)).limit(MAX_HITS);
        topicQuery.fields().include("_id", "firstPostId");
        Map<Integer, Double> firstPostScores = new HashMap<>();
        for (Document d : mongo.find(topicQuery, Document.class, mongo.getCollectionName(Topic.class))) {
            Integer firstPostId = d.getInteger("firstPostId");
            if (firstPostId != null) {
                firstPostScores.merge(firstPostId, number(d.get("score")), Double::sum);
            }
        }
        if (!firstPostScores.isEmpty()) {
            Query q = new Query(new Criteria().andOperator(filter, Criteria.where("_id").in(firstPostScores.keySet())));
            q.fields().include("_id", "time");
            for (Document d : mongo.find(q, Document.class, mongo.getCollectionName(Post.class))) {
                int id = d.getInteger("_id");
                double extra = firstPostScores.get(id);
                Hit old = merged.get(id);
                merged.put(id, new Hit(id, (old == null ? 0 : old.score()) + extra, toInstant(d.get("time"))));
            }
        }
        Comparator<Hit> order;
        if (args.isOrderByTime()) {
            order = Comparator.comparing(Hit::time, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingInt(Hit::id);
            if (!args.isAscending()) {
                order = order.reversed();
            }
        } else {
            order = Comparator.comparingDouble(Hit::score).reversed().thenComparing(Comparator.comparingInt(Hit::id).reversed());
        }
        return merged.values().stream().sorted(order).limit(MAX_HITS).toList();
    }

    private static TextCriteria textCriteria(SearchArgs args, String[] terms) {
        TextCriteria tc = TextCriteria.forDefaultLanguage();
        if (args.isMatchAll() && terms.length > 1) {
            for (String t : terms) {
                tc.matchingPhrase(t.replace("\"", ""));
            }
        } else {
            tc.matchingAny(terms);
        }
        return tc;
    }

    private static Criteria postFilter(SearchArgs args, List<Integer> forumIds) {
        Criteria c = Criteria.where("moderate").is(false).and("forumId").in(forumIds);
        if (args.getAuthorId() != null) {
            c = c.and("userId").is(args.getAuthorId());
        }
        if (args.getFromDate() != null || args.getToDate() != null) {
            Criteria time = c.and("time");
            if (args.getFromDate() != null) {
                time = time.gte(args.getFromDate());
            }
            if (args.getToDate() != null) {
                time.lte(args.getToDate());
            }
        }
        return c;
    }

    /** Legacy newMessages: topics with activity after the given instant (user's last visit), visible forums only. */
    public List<Topic> newMessages(PermissionControl pc, Instant since, int start, int perPage, long[] totalOut) {
        List<Integer> forumIds = forumService.visibleForums(pc).stream().map(Forum::getId).toList();
        Query q = new Query(Criteria.where("forumId").in(forumIds).and("lastPostTime").gt(since == null ? Instant.EPOCH : since)
            .and("movedId").isNull());
        totalOut[0] = mongo.count(q, Topic.class);
        return mongo.find(q.with(Sort.by(Sort.Direction.DESC, "lastPostTime", "_id")).skip(start).limit(perPage), Topic.class);
    }

    private static double number(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static Instant toInstant(Object o) {
        return o instanceof java.util.Date d ? d.toInstant() : null;
    }
}
