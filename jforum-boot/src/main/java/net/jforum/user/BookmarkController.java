package net.jforum.user;

import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.domain.Bookmark;
import net.jforum.domain.BookmarkType;
import net.jforum.domain.User;
import net.jforum.format.PostFormatter;
import net.jforum.i18n.I18n;
import net.jforum.repository.BookmarkRepository;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.CurrentUser;
import net.jforum.security.SecurityConstants;
import net.jforum.support.Ids;
import net.jforum.support.SequenceService;
import net.jforum.web.NotFoundException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Legacy module "bookmarks" (net.jforum.view.forum.BookmarkAction). Anonymous users may only list;
 * every action needs perm_bookmarks_enabled. Deletion is a POST (GET shows a confirmation).
 */
@Controller
public class BookmarkController {

    private final BookmarkRepository bookmarks;
    private final ForumRepository forums;
    private final TopicRepository topics;
    private final UserRepository users;
    private final SequenceService sequences;
    private final CurrentUser currentUser;
    private final PostFormatter formatter;
    private final I18n i18n;

    public BookmarkController(BookmarkRepository bookmarks, ForumRepository forums, TopicRepository topics, UserRepository users,
                              SequenceService sequences, CurrentUser currentUser, PostFormatter formatter, I18n i18n) {
        this.bookmarks = bookmarks;
        this.forums = forums;
        this.topics = topics;
        this.users = users;
        this.sequences = sequences;
        this.currentUser = currentUser;
        this.formatter = formatter;
        this.i18n = i18n;
    }

    /** Legacy BookmarkAction.process(): returns the error view, or null when the action may proceed. */
    private String guard(Model model, boolean listing) {
        if (!listing && !currentUser.isLogged()) {
            return error(model, "Bookmarks.anonymousIsDenied");
        }
        if (!currentUser.canAccess(SecurityConstants.PERM_BOOKMARKS_ENABLED)) {
            return error(model, "Bookmarks.featureDisabled");
        }
        return null;
    }

    private String error(Model model, String key) {
        model.addAttribute("messageTitle", i18n.getMessage("Bookmarks.myBag"));
        model.addAttribute("messageHtml", i18n.getMessage(key));
        return "user_message";
    }

    @GetMapping("/bookmarks/insert/{relationType}/{relationId}.page")
    public String insert(@PathVariable String relationType, @PathVariable String relationId, Model model) {
        String denied = guard(model, false);
        if (denied != null) {
            return denied;
        }
        Integer type = Ids.parse(relationType);
        Integer id = Ids.parse(relationId);
        BookmarkType bt = type == null ? BookmarkType.UNUSED : BookmarkType.fromLegacy(type);
        String title;
        String description = null;
        switch (bt) {
            case FORUM -> {
                var f = forums.findById(id == null ? -1 : id).orElseThrow(() -> new NotFoundException("invalidAction"));
                title = f.getName();
                description = f.getDescription();
            }
            case TOPIC -> title = topics.findById(id == null ? -1 : id).orElseThrow(() -> new NotFoundException("PostShow.TopicNotFound")).getTitle();
            case USER -> title = users.findById(id == null ? -1 : id).orElseThrow(() -> new NotFoundException("User.notFound")).getUsername();
            default -> {
                return error(model, "Bookmarks.invalidType");
            }
        }
        Optional<Bookmark> existing = bookmarks.findByUserIdAndRelationTypeAndRelationId(currentUser.getId(), bt, id);
        if (existing.isPresent()) {
            Bookmark b = existing.get();
            model.addAttribute("bookmark", b);
            title = b.getTitle() != null ? b.getTitle() : title;
            description = b.getDescription() != null ? b.getDescription() : description;
        }
        model.addAttribute("edit", existing.isPresent());
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("relationType", bt.legacyCode());
        model.addAttribute("relationId", id);
        model.addAttribute("visible", existing.map(Bookmark::isPublicVisible).orElse(true));
        return "bookmark_form";
    }

    @PostMapping("/bookmarks/insertSave.page")
    public String insertSave(@RequestParam(name = "relation_type") int relationType, @RequestParam(name = "relation_id") int relationId,
                             @RequestParam(required = false) String title, @RequestParam(required = false) String description,
                             @RequestParam(required = false) String visible, Model model) {
        String denied = guard(model, false);
        if (denied != null) {
            return denied;
        }
        BookmarkType bt = BookmarkType.fromLegacy(relationType);
        if (bt == BookmarkType.UNUSED) {
            return error(model, "Bookmarks.invalidType");
        }
        Bookmark b = bookmarks.findByUserIdAndRelationTypeAndRelationId(currentUser.getId(), bt, relationId).orElseGet(() -> {
            Bookmark n = new Bookmark();
            n.setId(sequences.next("bookmarks"));
            n.setUserId(currentUser.getId());
            n.setRelationType(bt);
            n.setRelationId(relationId);
            return n;
        });
        var safe = formatter.safeHtml();
        b.setTitle(safe.makeSafe(title));
        b.setDescription(safe.makeSafe(description));
        b.setPublicVisible(visible != null && !visible.isEmpty());
        bookmarks.save(b);
        return saved(model);
    }

    private String saved(Model model) {
        model.addAttribute("messageTitle", i18n.getMessage("Bookmarks.myBag"));
        model.addAttribute("messageHtml", i18n.getMessage("Bookmarks.saved"));
        model.addAttribute("closeLink", "/bookmarks/list/" + currentUser.getId() + ".page");
        return "user_message";
    }

    private Optional<Bookmark> owned(String id, Model model, String[] err) {
        Optional<Bookmark> b = Optional.ofNullable(Ids.parse(id)).flatMap(bookmarks::findById);
        if (b.isEmpty()) {
            err[0] = error(model, "Bookmarks.notFound");
        } else if (!b.get().getUserId().equals(currentUser.getId())) {
            err[0] = error(model, "Bookmarks.notOwner");
            return Optional.empty();
        }
        return b;
    }

    @GetMapping("/bookmarks/edit/{bookmarkId}.page")
    public String edit(@PathVariable String bookmarkId, Model model) {
        String denied = guard(model, false);
        if (denied != null) {
            return denied;
        }
        String[] err = new String[1];
        Optional<Bookmark> b = owned(bookmarkId, model, err);
        if (b.isEmpty()) {
            return err[0];
        }
        model.addAttribute("edit", true);
        model.addAttribute("bookmark", b.get());
        model.addAttribute("title", b.get().getTitle());
        model.addAttribute("description", b.get().getDescription());
        model.addAttribute("relationType", b.get().getRelationType().legacyCode());
        model.addAttribute("relationId", b.get().getRelationId());
        model.addAttribute("visible", b.get().isPublicVisible());
        return "bookmark_form";
    }

    @PostMapping("/bookmarks/updateSave.page")
    public String updateSave(@RequestParam(name = "bookmark_id") String bookmarkId, @RequestParam(required = false) String title,
                             @RequestParam(required = false) String description, @RequestParam(required = false) String visible,
                             Model model) {
        String denied = guard(model, false);
        if (denied != null) {
            return denied;
        }
        String[] err = new String[1];
        Optional<Bookmark> b = owned(bookmarkId, model, err);
        if (b.isEmpty()) {
            return err[0];
        }
        var safe = formatter.safeHtml();
        b.get().setTitle(safe.makeSafe(title));
        b.get().setDescription(safe.makeSafe(description));
        b.get().setPublicVisible(visible != null && !visible.isBlank());
        bookmarks.save(b.get());
        return saved(model);
    }

    @GetMapping("/bookmarks/delete/{bookmarkId}.page")
    public String deleteConfirm(@PathVariable String bookmarkId, Model model) {
        String denied = guard(model, false);
        if (denied != null) {
            return denied;
        }
        String[] err = new String[1];
        Optional<Bookmark> b = owned(bookmarkId, model, err);
        if (b.isEmpty()) {
            return err[0];
        }
        model.addAttribute("bookmark", b.get());
        model.addAttribute("confirmDelete", true);
        return "bookmark_form";
    }

    @PostMapping("/bookmarks/delete/{bookmarkId}.page")
    public String delete(@PathVariable String bookmarkId, Model model) {
        String denied = guard(model, false);
        if (denied != null) {
            return denied;
        }
        String[] err = new String[1];
        Optional<Bookmark> b = owned(bookmarkId, model, err);
        if (b.isEmpty()) {
            return err[0];
        }
        bookmarks.delete(b.get());
        return "redirect:/bookmarks/list/" + b.get().getUserId() + ".page";
    }

    @GetMapping("/bookmarks/list/{userId}.page")
    public String list(@PathVariable String userId, HttpServletRequest request, Model model) {
        String denied = guard(model, true);
        if (denied != null) {
            return denied;
        }
        User u = Optional.ofNullable(Ids.parse(userId)).flatMap(users::findById).orElseThrow(() -> new NotFoundException("User.notFound"));
        boolean owner = u.getId() == currentUser.getId();
        List<Bookmark> list = bookmarks.findByUserId(u.getId()).stream().filter(b -> owner || b.isPublicVisible()).toList();
        model.addAttribute("bookmarks", list);
        model.addAttribute("user", u);
        model.addAttribute("owner", owner);
        return "bookmark_list";
    }
}
