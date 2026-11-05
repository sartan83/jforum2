package net.jforum.admin;

import java.util.List;

import net.jforum.domain.Category;
import net.jforum.domain.Forum;
import net.jforum.domain.Topic;
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

/** Legacy CategoryAction (module adminCategories). */
@Controller
public class CategoryAdminController {

    private final CategoryRepository categories;
    private final ForumRepository forums;
    private final GroupRepository groups;
    private final GroupTree groupTree;
    private final PermissionService permissions;
    private final SequenceService sequences;
    private final MongoTemplate mongo;

    public CategoryAdminController(CategoryRepository categories, ForumRepository forums, GroupRepository groups,
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

    @GetMapping("/adminCategories/list.page")
    public String list(Model model) {
        model.addAttribute("categories", categories.findAllByOrderByOrderAsc());
        return "admin/category_list";
    }

    @GetMapping("/adminCategories/insert.page")
    public String insert(Model model) {
        model.addAttribute("groups", groupTree.nodes());
        model.addAttribute("action", "insertSave");
        return "admin/category_form";
    }

    @GetMapping("/adminCategories/edit/{categoryId}.page")
    public String edit(@PathVariable int categoryId, Model model) {
        model.addAttribute("category", find(categoryId));
        model.addAttribute("action", "editSave");
        return "admin/category_form";
    }

    @PostMapping("/adminCategories/insertSave.page")
    public String insertSave(@RequestParam("category_name") String name,
                             @RequestParam(value = "moderated", required = false) String moderated,
                             @RequestParam(value = "groups", required = false) String[] groupIds) {
        Category c = new Category();
        c.setId(sequences.next("categories"));
        c.setTitle(AdminSupport.trim(name));
        c.setModerated("1".equals(moderated));
        c.setOrder(categories.findAll().stream().mapToInt(Category::getOrder).max().orElse(0) + 1);
        categories.save(c);
        List<Integer> selected = AdminSupport.ids(groupIds);
        if (!selected.isEmpty()) {
            permissions.grant(SecurityConstants.PERM_CATEGORY, Integer.toString(c.getId()), selected);
        }
        return "redirect:/adminCategories/list.page";
    }

    @PostMapping("/adminCategories/editSave.page")
    public String editSave(@RequestParam("categories_id") int categoryId,
                           @RequestParam("category_name") String name,
                           @RequestParam(value = "moderated", required = false) String moderated) {
        Category c = find(categoryId);
        boolean moderate = "1".equals(moderated);
        boolean changed = c.isModerated() != moderate;
        c.setTitle(AdminSupport.trim(name));
        c.setModerated(moderate);
        categories.save(c);
        if (changed) {
            List<Integer> forumIds = forums.findByCategoryIdOrderByOrderAsc(categoryId).stream().map(Forum::getId).toList();
            mongo.updateMulti(Query.query(Criteria.where("categoryId").is(categoryId)), Update.update("moderated", moderate), Forum.class);
            mongo.updateMulti(Query.query(Criteria.where("forumId").in(forumIds)), Update.update("moderated", moderate), Topic.class);
        }
        return "redirect:/adminCategories/list.page";
    }

    @PostMapping("/adminCategories/delete.page")
    public String delete(@RequestParam(value = "categories_id", required = false) String[] ids, Model model) {
        StringBuilder errors = new StringBuilder();
        for (Integer id : AdminSupport.ids(ids)) {
            if (!forums.findByCategoryIdOrderByOrderAsc(id).isEmpty()) {
                categories.findById(id).ifPresent(c -> errors.append(errors.isEmpty() ? "" : ", ").append(c.getTitle()));
                continue;
            }
            categories.deleteById(id);
            permissions.revokeValue(SecurityConstants.PERM_CATEGORY, Integer.toString(id));
        }
        if (!errors.isEmpty()) {
            model.addAttribute("errorMessage", errors.toString());
        }
        return list(model);
    }

    @GetMapping("/adminCategories/up/{categoryId}.page")
    public String up(@PathVariable int categoryId) {
        move(categoryId, -1);
        return "redirect:/adminCategories/list.page";
    }

    @GetMapping("/adminCategories/down/{categoryId}.page")
    public String down(@PathVariable int categoryId) {
        move(categoryId, 1);
        return "redirect:/adminCategories/list.page";
    }

    private void move(int categoryId, int direction) {
        List<Category> all = categories.findAllByOrderByOrderAsc();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId() == categoryId) {
                int j = i + direction;
                if (j >= 0 && j < all.size()) {
                    Category a = all.get(i);
                    Category b = all.get(j);
                    int order = a.getOrder();
                    a.setOrder(b.getOrder() == order ? order + direction : b.getOrder());
                    b.setOrder(order);
                    categories.saveAll(List.of(a, b));
                }
                return;
            }
        }
    }

    private Category find(int id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found"));
    }

}
