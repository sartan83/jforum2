package net.jforum.forum;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Forum;
import net.jforum.domain.Topic;
import net.jforum.domain.User;
import net.jforum.security.CurrentUser;
import net.jforum.security.PermissionControl;
import net.jforum.security.SecurityConstants;
import net.jforum.support.Ids;
import net.jforum.support.Pagination;
import net.jforum.topic.ReadTracker;
import net.jforum.topic.TopicService;
import net.jforum.web.NotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** Legacy ForumAction show/readAll/newMessages/watchForum/unwatchForum, RecentTopicsAction and HottestTopicsAction. */
@Controller
public class ForumTopicsController {

    private final ForumService forumService;
    private final ForumWatchService forumWatches;
    private final TopicService topicService;
    private final CurrentUser currentUser;
    private final ForumConfig config;

    public ForumTopicsController(ForumService forumService, ForumWatchService forumWatches, TopicService topicService,
                                 CurrentUser currentUser, ForumConfig config) {
        this.forumService = forumService;
        this.forumWatches = forumWatches;
        this.topicService = topicService;
        this.currentUser = currentUser;
        this.config = config;
    }

    private Forum requireForum(String forumId) {
        Integer id = Ids.parse(forumId);
        if (id == null) {
            throw new NotFoundException("ForumListing.forumNotFound");
        }
        return forumService.requireAccessible(currentUser.permissions(), id);
    }

    @GetMapping({"/forums/show/{forumId}.page", "/forums/show/{start}/{forumId}.page"})
    public String show(@PathVariable String forumId, @PathVariable(required = false) Integer start, Model model, HttpSession session) {
        Forum forum = requireForum(forumId);
        PermissionControl pc = currentUser.permissions();
        int per = config.topicsPerPage();
        int s = Pagination.normalizeStart(start, per);
        List<Topic> list = topicService.forumTopics(forum.getId(), s, per);
        long total = topicService.countForumTopics(forum.getId());
        model.addAttribute("forum", forum);
        model.addAttribute("topics", topicService.prepare(list, session, currentUser.get(), currentUser.isLogged()));
        model.addAttribute("pagination", new Pagination(s, per, total, "/forums/show", String.valueOf(forum.getId())));
        model.addAttribute("watching", currentUser.isLogged() && forumWatches.isWatching(forum.getId(), currentUser.getId()));
        model.addAttribute("readonly", !pc.canAccess(SecurityConstants.PERM_READ_ONLY_FORUMS, forum.getId()));
        model.addAttribute("replyOnly", !pc.canAccess(SecurityConstants.PERM_REPLY_ONLY, forum.getId()));
        model.addAttribute("isModerator", pc.isModerator(forum.getId()));
        return "forum_show";
    }

    @GetMapping({"/forums/readAll/{forumId}.page", "/forums/readAll.page"})
    public String readAll(@PathVariable(required = false) String forumId, HttpSession session) {
        if (forumId != null) {
            Forum forum = requireForum(forumId);
            ReadTracker.markForumRead(session, forum.getId());
            ReadTracker.flush(session);
            return "redirect:/forums/show/" + forum.getId() + ".page";
        }
        for (Forum f : forumService.visibleForums(currentUser.permissions())) {
            ReadTracker.markForumRead(session, f.getId());
        }
        ReadTracker.flush(session);
        return "redirect:/forums/list.page";
    }

    @GetMapping({"/forums/newMessages.page", "/forums/newMessages/{start}.page"})
    public String newMessages(@PathVariable(required = false) Integer start, Model model, HttpSession session) {
        User user = currentUser.get();
        Instant since = !currentUser.isLogged() ? Instant.now()
            : user.getLastVisit() != null ? user.getLastVisit()
            : user.getRegistrationDate() != null ? user.getRegistrationDate() : Instant.EPOCH;
        List<Integer> forumIds = forumService.visibleForums(currentUser.permissions()).stream().map(Forum::getId).toList();
        int per = config.topicsPerPage();
        int s = Pagination.normalizeStart(start, per);
        long total = topicService.countNewMessages(forumIds, since);
        model.addAttribute("topics", topicService.prepare(topicService.newMessages(forumIds, since, s, per), session, user,
            currentUser.isLogged()));
        model.addAttribute("start", s);
        model.addAttribute("perPage", per);
        model.addAttribute("totalPages", (int) Math.ceil(total / (double) per));
        return "new_messages";
    }

    @GetMapping({"/forums/watchForum/{forumId}.page", "/forums/watchForum.page"})
    public String watchForum(@PathVariable(required = false) String forumId,
                             @RequestParam(name = "forum_id", required = false) String forumIdParam) {
        if (!currentUser.isLogged()) {
            return "redirect:/user/login.page";
        }
        Forum forum = requireForum(forumId != null ? forumId : forumIdParam);
        forumWatches.watch(forum.getId(), currentUser.getId());
        return "redirect:/forums/show/" + forum.getId() + ".page";
    }

    @GetMapping({"/forums/unwatchForum/{forumId}.page", "/forums/unwatchForum.page"})
    public String unwatchForum(@PathVariable(required = false) String forumId,
                               @RequestParam(name = "forum_id", required = false) String forumIdParam, Model model) {
        if (!currentUser.isLogged()) {
            return "redirect:/user/login.page";
        }
        Forum forum = requireForum(forumId != null ? forumId : forumIdParam);
        forumWatches.unwatch(forum.getId(), currentUser.getId());
        model.addAttribute("noticeKey", "ForumBase.forumUnwatched");
        model.addAttribute("returnPath", "/forums/show/" + forum.getId() + ".page");
        return "notice";
    }

    @GetMapping("/recentTopics/list.page")
    public String recentTopics(Model model, HttpSession session) {
        List<Integer> ids = forumService.visibleForums(currentUser.permissions()).stream().map(Forum::getId).toList();
        List<Topic> list = topicService.recentTopics(ids, config.getInt("topic.recent", 50));
        model.addAttribute("topics", topicService.prepare(list, session, currentUser.get(), currentUser.isLogged()));
        model.addAttribute("titleKey", "ForumBase.recentTopics");
        return "recent_topics";
    }

    @GetMapping("/hottestTopics/list.page")
    public String hottestTopics(Model model, HttpSession session) {
        List<Integer> ids = forumService.visibleForums(currentUser.permissions()).stream().map(Forum::getId).toList();
        List<Topic> list = topicService.hottestTopics(ids, config.getInt("topic.hottest", 50));
        model.addAttribute("topics", topicService.prepare(list, session, currentUser.get(), currentUser.isLogged()));
        model.addAttribute("titleKey", "ForumBase.hottestTopics");
        return "recent_topics";
    }
}
