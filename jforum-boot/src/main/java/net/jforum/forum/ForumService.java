package net.jforum.forum;

import java.util.ArrayList;
import java.util.List;

import net.jforum.domain.Category;
import net.jforum.domain.Forum;
import net.jforum.repository.CategoryRepository;
import net.jforum.repository.ForumRepository;
import net.jforum.security.PermissionControl;
import net.jforum.security.SecurityConstants;
import net.jforum.web.ForbiddenException;
import net.jforum.web.NotFoundException;

import org.springframework.stereotype.Service;

/** Category/forum reads filtered by perm_category and perm_forum (legacy ForumRepository). */
@Service
public class ForumService {

    private final CategoryRepository categories;
    private final ForumRepository forums;

    public ForumService(CategoryRepository categories, ForumRepository forums) {
        this.categories = categories;
        this.forums = forums;
    }

    public static final class CategoryView {

        private final Category category;
        private final List<Forum> forums;

        public CategoryView(Category category, List<Forum> forums) {
            this.category = category;
            this.forums = forums;
        }

        public Category getCategory() {
            return category;
        }

        public List<Forum> getForums() {
            return forums;
        }
    }

    /** Categories (and their forums) visible to the given permissions, in display order. */
    public List<CategoryView> visibleCategories(PermissionControl pc) {
        List<CategoryView> result = new ArrayList<>();
        for (Category c : categories.findAllByOrderByOrderAsc()) {
            if (!pc.canAccess(SecurityConstants.PERM_CATEGORY, c.getId())) {
                continue;
            }
            List<Forum> visible = forums.findByCategoryIdOrderByOrderAsc(c.getId()).stream()
                .filter(f -> pc.canAccess(SecurityConstants.PERM_FORUM, f.getId()))
                .toList();
            if (!visible.isEmpty()) {
                result.add(new CategoryView(c, visible));
            }
        }
        return result;
    }

    public List<Forum> visibleForums(PermissionControl pc) {
        return visibleCategories(pc).stream().flatMap(c -> c.getForums().stream()).toList();
    }

    public boolean isForumAccessible(PermissionControl pc, Forum forum) {
        return pc.canAccess(SecurityConstants.PERM_FORUM, forum.getId())
            && pc.canAccess(SecurityConstants.PERM_CATEGORY, forum.getCategoryId());
    }

    /** Loads a forum checking it exists and is accessible, like legacy ForumAction.show. */
    public Forum requireAccessible(PermissionControl pc, int forumId) {
        Forum forum = forums.findById(forumId).orElseThrow(() -> new NotFoundException("ForumListing.forumNotFound"));
        if (!isForumAccessible(pc, forum)) {
            throw new ForbiddenException("ForumListing.denied");
        }
        return forum;
    }
}
