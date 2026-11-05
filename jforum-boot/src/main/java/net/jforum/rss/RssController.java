package net.jforum.rss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Forum;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.domain.User;
import net.jforum.format.PostFormatter;
import net.jforum.forum.ForumService;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.CurrentUser;
import net.jforum.security.PermissionControl;
import net.jforum.web.NotFoundException;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of legacy RSSAction + util/rss (TopicRSS, TopicPostsRSS, RecentTopicsRSS): RSS 2.0, gated by
 * rss.enabled and the forum/category read permissions, absolute links based on forum.link.
 */
@RestController
public class RssController {

    static final MediaType TEXT_XML = MediaType.parseMediaType("text/xml;charset=UTF-8");

    private final ForumConfig config;
    private final ForumService forumService;
    private final TopicRepository topics;
    private final PostRepository posts;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final PostFormatter formatter;
    private final CurrentUser currentUser;
    private final MessageSource messages;

    public RssController(ForumConfig config, ForumService forumService, TopicRepository topics, PostRepository posts,
                         UserRepository users, MongoTemplate mongo, PostFormatter formatter, CurrentUser currentUser,
                         MessageSource messages) {
        this.config = config;
        this.forumService = forumService;
        this.topics = topics;
        this.posts = posts;
        this.users = users;
        this.mongo = mongo;
        this.formatter = formatter;
        this.currentUser = currentUser;
        this.messages = messages;
    }

    @GetMapping({"/rss/forums.page", "/rss/forums"})
    public ResponseEntity<String> forums() {
        requireEnabled();
        List<RssItem> items = new ArrayList<>();
        for (Forum f : forumService.visibleForums(currentUser.permissions())) {
            items.add(new RssItem(f.getName(), link("forums/show/" + f.getId() + ".page"), f.getDescription(),
                f.getLastPostInfo() == null ? null : f.getLastPostInfo().getPostTime(), null));
        }
        return xml(RssWriter.write(config.forumName(), link("forums/list.page"), config.get("forum.page.metatag.description", ""), items));
    }

    @GetMapping({"/rss/forumTopics/{forumId}.page"})
    public ResponseEntity<String> forumTopics(@PathVariable int forumId) {
        requireEnabled();
        Forum forum = forumService.requireAccessible(currentUser.permissions(), forumId);
        List<Topic> latest = mongo.find(Query.query(Criteria.where("forumId").is(forumId).and("movedId").isNull())
            .with(Sort.by(Sort.Direction.DESC, "_id")).limit(limit()), Topic.class);
        return xml(RssWriter.write(msg("RSS.ForumTopics.title", forum.getName()), link("forums/show/" + forumId + ".page"),
            msg("RSS.ForumTopics.description", forum.getName()), firstPostItems(latest)));
    }

    @GetMapping({"/rss/topicPosts/{topicId}.page"})
    public ResponseEntity<String> topicPosts(@PathVariable int topicId) {
        requireEnabled();
        Topic topic = topics.findById(topicId).orElseThrow(() -> new NotFoundException("PostShow.TopicNotFound"));
        forumService.requireAccessible(currentUser.permissions(), topic.getForumId());
        mongo.updateFirst(Query.query(Criteria.where("_id").is(topicId)), new Update().inc("views", 1), Topic.class);
        List<RssItem> items = new ArrayList<>();
        for (Post p : mongo.find(Query.query(Criteria.where("topicId").is(topicId).and("moderate").is(false))
            .with(Sort.by("time", "_id")), Post.class)) {
            items.add(item(p, p.getSubject() == null || p.getSubject().isBlank() ? topic.getTitle() : p.getSubject()));
        }
        return xml(RssWriter.write(msg("RSS.TopicPosts.title", topic.getTitle()), link("posts/list/" + topicId + ".page"),
            msg("RSS.TopicPosts.description", topic.getTitle()), items));
    }

    @GetMapping({"/rss/recentTopics.page", "/rss/recentTopics"})
    public ResponseEntity<String> recentTopics() {
        requireEnabled();
        List<Integer> forumIds = forumService.visibleForums(currentUser.permissions()).stream().map(Forum::getId).toList();
        List<Topic> latest = mongo.find(Query.query(Criteria.where("forumId").in(forumIds).and("movedId").isNull())
            .with(Sort.by(Sort.Direction.DESC, "lastPostTime", "_id")).limit(limit()), Topic.class);
        return xml(RssWriter.write(msg("RSS.RecentTopics.title", config.forumName()), link("recentTopics/list.page"),
            msg("RSS.RecentTopics.description"), firstPostItems(latest)));
    }

    @GetMapping({"/rss/userTopics/{userId}.page"})
    public ResponseEntity<String> userTopics(@PathVariable int userId) {
        requireEnabled();
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User.notFound"));
        PermissionControl pc = currentUser.permissions();
        List<Integer> forumIds = forumService.visibleForums(pc).stream().map(Forum::getId).toList();
        List<Topic> list = mongo.find(Query.query(Criteria.where("userId").is(userId).and("forumId").in(forumIds))
            .with(Sort.by(Sort.Direction.DESC, "_id")).limit(limit()), Topic.class);
        return xml(RssWriter.write(msg("RSS.TopicsByUser.title", user.getUsername()), link("user/profile/" + userId + ".page"),
            msg("RSS.TopicsByUser.description", user.getUsername()), firstPostItems(list)));
    }

    private List<RssItem> firstPostItems(List<Topic> list) {
        Map<Integer, Post> first = new HashMap<>();
        posts.findAllById(list.stream().map(Topic::getFirstPostId).filter(id -> id != null).toList())
            .forEach(p -> first.put(p.getId(), p));
        Map<Integer, Topic> byId = list.stream().collect(Collectors.toMap(Topic::getId, Function.identity()));
        List<RssItem> items = new ArrayList<>();
        for (Topic t : byId.values().stream().sorted((a, b) -> list.indexOf(a) - list.indexOf(b)).toList()) {
            Post p = t.getFirstPostId() == null ? null : first.get(t.getFirstPostId());
            if (p != null && !p.isModerate()) {
                items.add(item(p, t.getTitle()));
            }
        }
        return items;
    }

    private RssItem item(Post p, String title) {
        // Same as legacy PostCommon.preparePostForDisplay(post).getText()
        String description = formatter.format(p.getText(), p.isHtmlEnabled(), p.isBbCodeEnabled(), p.isSmiliesEnabled());
        return new RssItem(title, link("posts/preList/" + p.getTopicId() + "/" + p.getId() + ".page"), description, p.getTime(),
            p.getUsername());
    }

    private int limit() {
        return Math.max(1, config.topicsPerPage());
    }

    private void requireEnabled() {
        if (!config.getBool(ConfigKeys.RSS_ENABLED)) {
            throw new NotFoundException("RSS.disabled");
        }
    }

    private String link(String path) {
        String base = config.get(ConfigKeys.FORUM_LINK, "http://localhost:8080/");
        return (base.endsWith("/") ? base : base + "/") + path;
    }

    private String msg(String key, Object... args) {
        return messages.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    private static ResponseEntity<String> xml(String body) {
        return ResponseEntity.ok().contentType(TEXT_XML).body(body);
    }
}
