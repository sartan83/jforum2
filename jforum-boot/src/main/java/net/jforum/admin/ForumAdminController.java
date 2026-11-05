package net.jforum.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.jforum.domain.Category;
import net.jforum.domain.Forum;
import net.jforum.domain.Group;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicWatch;
import net.jforum.repository.CategoryRepository;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.GroupRepository;
import net.jforum.security.PermissionService;
import net.jforum.security.SecurityConstants;
import net.jforum.support.SequenceService;
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

/** Legacy ForumAction (module adminForums). */
@Controller
public class ForumAdminController {

    private final CategoryRepository categories;
    private final ForumRepository forums;
    private final GroupRepository groups;
    private final GroupTree groupTree;
    private final PermissionService permissions;
    private final SequenceService sequences;
    private final MongoTemplate mongo;

    public ForumAdminController(CategoryRepository categories, ForumRepository forums, GroupRepository groups,
                                GroupTree groupTree, PermissionService permissions, SequenceService sequences,
                                MongoTemplate mongo) {
        this.categories = categories;
        this.forums = forums;
        this.groups = groups;
        this.groupTree = groupTree;
        this.permissions = permissions;
        this.sequences = sequences;
        this.mongo = mongo;
    }

    @GetMapping("/adminForums/list.page")
    public String list(Model model) {
        Map<Category, List<Forum>> tree = new LinkedHashMap<>();
        for (Category c : categories.findAllByOrderByOrderAsc()) {
            tree.put(c, forums.findByCategoryIdOrderByOrderAsc(c.getId()));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        tree.forEach((c, fs) -> rows.add(Map.of("category", c, "forums", fs)));
        model.addAttribute("categories", rows);
        return "admin/forum_list";
    }

    @GetMapping("/adminForums/insert.page")
    public String insert(Model model) {
        model.addAttribute("categories", categories.findAllByOrderByOrderAsc());
        model.addAttribute("groups", groupTree.nodes());
        model.addAttribute("action", "insertSave");
        return "admin/forum_form";
    }

    @GetMapping("/adminForums/edit/{forumId}.page")
    public String edit(@PathVariable int forumId, Model model) {
        model.addAttribute("forum", find(forumId));
        model.addAttribute("categories", categories.findAllByOrderByOrderAsc());
        model.addAttribute("action", "editSave");
        return "admin/forum_form";
    }

    @PostMapping("/adminForums/insertSave.page")
    public String insertSave(@RequestParam("forum_name") String name,
                             @RequestParam(value = "description", required = false) String description,
                             @RequestParam("categories_id") int categoryId,
                             @RequestParam(value = "moderate", required = false) String moderate,
                             @RequestParam(value = "groups", required = false) String[] allGroups,
                             @RequestParam(value = "groupsAccess", required = false) String[] groupsAccess,
                             @RequestParam(value = "groupsAnonymous", required = false) String[] groupsAnonymous,
                             @RequestParam(value = "groupsReadOnly", required = false) String[] groupsReadOnly,
                             @RequestParam(value = "groupsHtml", required = false) String[] groupsHtml) {
        if (categories.findById(categoryId).isEmpty()) {
            throw new NotFoundException("Category not found");
        }
        Forum f = new Forum();
        f.setId(sequences.next("forums"));
        f.setName(AdminSupport.trim(name));
        f.setDescription(AdminSupport.trim(description));
        f.setCategoryId(categoryId);
        f.setModerated("1".equals(moderate));
        f.setOrder(forums.findAll().stream().mapToInt(Forum::getOrder).max().orElse(0) + 1);
        forums.save(f);

        String id = Integer.toString(f.getId());
        List<Integer> all = AdminSupport.ids(allGroups);
        if (allGroups == null) {
            all = groups.findAll().stream().map(Group::getId).toList();
        }
        grant(SecurityConstants.PERM_FORUM, id, groupsAccess, all);
        grant(SecurityConstants.PERM_ANONYMOUS_POST, id, groupsAnonymous, all);
        grant(SecurityConstants.PERM_READ_ONLY_FORUMS, id, groupsReadOnly, all);
        grant(SecurityConstants.PERM_REPLY_ONLY, id, null, all);
        grant(SecurityConstants.PERM_HTML_DISABLED, id, groupsHtml, all);
        permissions.invalidate();
        return "redirect:/adminForums/list.page";
    }

    private void grant(String role, String forumId, String[] selected, List<Integer> fallback) {
        List<Integer> target = selected != null ? AdminSupport.ids(selected) : fallback;
        if (!target.isEmpty()) {
            permissions.grant(role, forumId, target);
        }
    }

    @PostMapping("/adminForums/editSave.page")
    public String editSave(@RequestParam("forum_id") int forumId,
                           @RequestParam("forum_name") String name,
                           @RequestParam(value = "description", required = false) String description,
                           @RequestParam("categories_id") int categoryId,
                           @RequestParam(value = "moderate", required = false) String moderate) {
        Forum f = find(forumId);
        boolean moderated = "1".equals(moderate);
        boolean changed = f.isModerated() != moderated;
        f.setName(AdminSupport.trim(name));
        f.setDescription(AdminSupport.trim(description));
        if (categories.findById(categoryId).isPresent()) {
            f.setCategoryId(categoryId);
        }
        f.setModerated(moderated);
        forums.save(f);
        if (changed) {
            mongo.updateMulti(Query.query(Criteria.where("forumId").is(forumId)), Update.update("moderated", moderated), Topic.class);
        }
        return "redirect:/adminForums/list.page";
    }

    @PostMapping("/adminForums/delete.page")
    public String delete(@RequestParam(value = "forum_id", required = false) String[] ids) {
        for (Integer forumId : AdminSupport.ids(ids)) {
            List<Integer> topicIds = mongo.find(Query.query(Criteria.where("forumId").is(forumId)), Topic.class)
                .stream().map(Topic::getId).toList();
            mongo.remove(Query.query(Criteria.where("topicId").in(topicIds)), TopicWatch.class);
            mongo.remove(Query.query(Criteria.where("forumId").is(forumId)), Post.class);
            mongo.remove(Query.query(Criteria.where("forumId").is(forumId)), Topic.class);
            forums.deleteById(forumId);
            String value = Integer.toString(forumId);
            for (String role : List.of(SecurityConstants.PERM_FORUM, SecurityConstants.PERM_ANONYMOUS_POST,
                SecurityConstants.PERM_READ_ONLY_FORUMS, SecurityConstants.PERM_REPLY_ONLY,
                SecurityConstants.PERM_HTML_DISABLED, SecurityConstants.PERM_MODERATION_FORUMS)) {
                permissions.revokeValue(role, value);
            }
        }
        return "redirect:/adminForums/list.page";
    }

    @GetMapping("/adminForums/up/{forumId}.page")
    public String up(@PathVariable int forumId) {
        move(forumId, -1);
        return "redirect:/adminForums/list.page";
    }

    @GetMapping("/adminForums/down/{forumId}.page")
    public String down(@PathVariable int forumId) {
        move(forumId, 1);
        return "redirect:/adminForums/list.page";
    }

    private void move(int forumId, int direction) {
        Forum f = find(forumId);
        List<Forum> siblings = forums.findByCategoryIdOrderByOrderAsc(f.getCategoryId());
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId() == forumId) {
                int j = i + direction;
                if (j >= 0 && j < siblings.size()) {
                    Forum a = siblings.get(i);
                    Forum b = siblings.get(j);
                    int order = a.getOrder();
                    a.setOrder(b.getOrder() == order ? order + direction : b.getOrder());
                    b.setOrder(order);
                    forums.saveAll(List.of(a, b));
                }
                return;
            }
        }
    }

    private Forum find(int id) {
        return forums.findById(id).orElseThrow(() -> new NotFoundException("Forum not found"));
    }
}
