package net.jforum.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.domain.Category;
import net.jforum.domain.Forum;
import net.jforum.domain.LastPostInfo;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.domain.User;
import net.jforum.repository.AttachmentRepository;
import net.jforum.repository.CategoryRepository;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.UserRepository;
import net.jforum.web.NotFoundException;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Legacy admin ModerationAction (module adminModeration): queue of posts with moderate=true. Approving clears the flag
 * and updates topic/forum/user counters; rejecting deletes the post (and its topic when it was the only post).
 */
@Controller
public class ModerationAdminController {

    private final CategoryRepository categories;
    private final ForumRepository forums;
    private final TopicRepository topics;
    private final PostRepository posts;
    private final UserRepository users;
    private final AttachmentRepository attachments;
    private final MongoTemplate mongo;

    public ModerationAdminController(CategoryRepository categories, ForumRepository forums, TopicRepository topics,
                                     PostRepository posts, UserRepository users, AttachmentRepository attachments,
                                     MongoTemplate mongo) {
        this.categories = categories;
        this.forums = forums;
        this.topics = topics;
        this.posts = posts;
        this.users = users;
        this.attachments = attachments;
        this.mongo = mongo;
    }

    public static final class ForumPending {

        private final Forum forum;
        private final long total;

        ForumPending(Forum forum, long total) {
            this.forum = forum;
            this.total = total;
        }

        public Forum getForum() {
            return forum;
        }

        public long getTotal() {
            return total;
        }
    }

    @GetMapping("/adminModeration/list.page")
    public String list(Model model) {
        Map<Integer, Long> pending = new TreeMap<>();
        posts.findByModerateTrue().forEach(p -> pending.merge(p.getForumId(), 1L, Long::sum));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Category c : categories.findAllByOrderByOrderAsc()) {
            List<ForumPending> fs = new ArrayList<>();
            for (Forum f : forums.findByCategoryIdOrderByOrderAsc(c.getId())) {
                if (f.isModerated() || pending.containsKey(f.getId())) {
                    fs.add(new ForumPending(f, pending.getOrDefault(f.getId(), 0L)));
                }
            }
            if (!fs.isEmpty()) {
                rows.add(Map.of("category", c, "forums", fs));
            }
        }
        model.addAttribute("categories", rows);
        return "admin/moderation_list";
    }

    @GetMapping("/adminModeration/view/{forumId}.page")
    public String view(@PathVariable int forumId, Model model) {
        Forum forum = forums.findById(forumId).orElseThrow(() -> new NotFoundException("Forum not found"));
        List<Post> pending = mongo.find(Query.query(Criteria.where("forumId").is(forumId).and("moderate").is(true)), Post.class);
        pending.sort(Comparator.comparing(Post::getTopicId).thenComparing(Post::getTime, Comparator.nullsFirst(Comparator.naturalOrder())));
        Map<Integer, Map<String, Object>> byTopic = new LinkedHashMap<>();
        for (Post p : pending) {
            byTopic.computeIfAbsent(p.getTopicId(), id -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("topic", topics.findById(id).orElse(null));
                m.put("posts", new ArrayList<Post>());
                return m;
            });
            @SuppressWarnings("unchecked")
            List<Post> list = (List<Post>) byTopic.get(p.getTopicId()).get("posts");
            list.add(p);
        }
        model.addAttribute("forum", forum);
        model.addAttribute("topics", new ArrayList<>(byTopic.values()));
        return "admin/moderation_list_posts";
    }

    @PostMapping("/adminModeration/save.page")
    public String save(@RequestParam("forum_id") int forumId,
                       @RequestParam(value = "post_id", required = false) String[] postIds, HttpServletRequest request) {
        for (Integer postId : AdminSupport.ids(postIds)) {
            String status = request.getParameter("status_" + postId);
            if ("aprove".equals(status) || "approve".equals(status)) {
                approve(postId);
            } else if ("reject".equals(status)) {
                reject(postId);
            }
        }
        return "redirect:/adminModeration/view/" + forumId + ".page";
    }

    private void approve(int postId) {
        Post post = posts.findById(postId).orElse(null);
        if (post == null || !post.isModerate()) {
            return;
        }
        post.setModerate(false);
        posts.save(post);
        Topic topic = topics.findById(post.getTopicId()).orElse(null);
        if (topic == null) {
            return;
        }
        boolean firstPost = post.getId().equals(topic.getFirstPostId());
        Update topicUpdate = new Update();
        if (!firstPost) {
            topicUpdate.inc("replies", 1);
        }
        if (topic.getLastPostTime() == null || post.getTime() == null || !post.getTime().isBefore(topic.getLastPostTime())) {
            topicUpdate.set("lastPostId", post.getId()).set("lastPostTime", post.getTime())
                .set("lastPostUserId", post.getUserId()).set("lastPostUsername", post.getUsername());
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(topic.getId())), topicUpdate, Topic.class);

        Forum forum = forums.findById(post.getForumId()).orElse(null);
        if (forum != null) {
            forum.setTotalPosts(forum.getTotalPosts() + 1);
            if (firstPost) {
                forum.setTotalTopics(forum.getTotalTopics() + 1);
            }
            LastPostInfo last = forum.getLastPostInfo();
            if (last == null || last.getPostTime() == null || post.getTime() == null || !post.getTime().isBefore(last.getPostTime())) {
                LastPostInfo info = new LastPostInfo();
                info.setPostId(post.getId());
                info.setTopicId(topic.getId());
                info.setUserId(post.getUserId());
                info.setUsername(post.getUsername());
                info.setPostTime(post.getTime());
                info.setTopicReplies(topic.getReplies() + (firstPost ? 0 : 1));
                forum.setLastPostInfo(info);
                forum.setLastPostId(post.getId());
            }
            forums.save(forum);
        }
        if (post.getUserId() != null) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(post.getUserId())), new Update().inc("totalPosts", 1), User.class);
        }
    }

    private void reject(int postId) {
        Post post = posts.findById(postId).orElse(null);
        if (post == null || !post.isModerate()) {
            return;
        }
        attachments.deleteAll(attachments.findByPostId(postId));
        posts.delete(post);
        Topic topic = topics.findById(post.getTopicId()).orElse(null);
        if (topic != null && mongo.count(Query.query(Criteria.where("topicId").is(topic.getId())), Post.class) == 0) {
            topics.delete(topic);
        }
    }
}
