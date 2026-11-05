package net.jforum.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.jforum.domain.Post;
import net.jforum.repository.PostRepository;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Legacy LuceneStatsAction (module adminSearchStats): statistics of the MongoDB text index used for searching posts. */
@Controller
public class SearchStatsAdminController {

    private final MongoTemplate mongo;
    private final PostRepository posts;

    public SearchStatsAdminController(MongoTemplate mongo, PostRepository posts) {
        this.mongo = mongo;
        this.posts = posts;
    }

    @GetMapping("/adminSearchStats/list.page")
    public String list(Model model) {
        String collection = mongo.getCollectionName(Post.class);
        List<String> textIndexes = new ArrayList<>();
        for (Document index : mongo.getCollection(collection).listIndexes()) {
            Object key = index.get("key");
            if (key instanceof Document d && d.containsValue("text")) {
                textIndexes.add(index.getString("name"));
            }
        }
        Document stats = mongo.getDb().runCommand(new Document("collStats", collection));
        Object sizes = stats.get("indexSizes");
        long indexSize = 0;
        if (sizes instanceof Map<?, ?> m) {
            for (String name : textIndexes) {
                Object v = m.get(name);
                if (v instanceof Number n) {
                    indexSize += n.longValue();
                }
            }
        }
        model.addAttribute("collection", collection);
        model.addAttribute("indexExists", !textIndexes.isEmpty());
        model.addAttribute("indexNames", String.join(", ", textIndexes));
        model.addAttribute("indexSize", indexSize);
        model.addAttribute("totalPosts", posts.count());
        model.addAttribute("numberOfDocs", stats.get("count") instanceof Number n ? n.longValue() : posts.count());
        return "admin/search_stats_list";
    }

    @PostMapping("/adminSearchStats/check.page")
    public String check(@RequestParam("post_id") String postId, Model model) {
        Integer id = net.jforum.support.Ids.parse(postId);
        model.addAttribute("checkedPostId", postId);
        model.addAttribute("postIndexed", id != null && posts.existsById(id));
        return list(model);
    }
}
