package net.jforum.moderation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.config.ForumConfig;
import net.jforum.domain.Forum;
import net.jforum.domain.ModerationLog;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.forum.ForumService;
import net.jforum.i18n.I18n;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.ModerationLogRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.search.OffsetPagination;
import net.jforum.security.CurrentUser;
import net.jforum.security.PermissionControl;
import net.jforum.security.SecurityConstants;
import net.jforum.support.Pagination;
import net.jforum.web.ForbiddenException;
import net.jforum.web.NotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

/**
 * Port of legacy forum ModerationAction (doModeration, moveTopic, moderationDone, showActivityLog) and of the
 * pending-posts queue of admin ModerationAction, made available to forum moderators.
 *
 * <p>Permission checks follow legacy ModerationHelper: perm_moderation plus the operation specific role
 * (perm_moderation_post_remove / perm_moderation_topic_move / perm_moderation_topic_lockUnlock), and every
 * affected topic must belong to a forum listed in perm_moderation_forums.
 */
@Controller
public class ModerationController {

    private final ModerationService service;
    private final TopicRepository topics;
    private final ForumRepository forums;
    private final ModerationLogRepository logs;
    private final ForumService forumService;
    private final CurrentUser currentUser;
    private final ForumConfig config;
    private final I18n i18n;

    public ModerationController(ModerationService service, TopicRepository topics, ForumRepository forums,
                                ModerationLogRepository logs, ForumService forumService, CurrentUser currentUser,
                                ForumConfig config, I18n i18n) {
        this.i18n = i18n;
        this.service = service;
        this.topics = topics;
        this.forums = forums;
        this.logs = logs;
        this.forumService = forumService;
        this.currentUser = currentUser;
        this.config = config;
    }

    // ------------------------------------------------------------------ doModeration

    @PostMapping({"/moderation/doModeration.page", "/moderation/doModeration"})
    public String doModeration(@RequestParam(name = "topic_id", required = false) List<Integer> topicIds,
                               @RequestParam(name = "returnUrl", required = false) String returnUrl,
                               @RequestParam(name = "log_description", required = false) String reason,
                               HttpServletRequest request, Model model) {
        PermissionControl pc = currentUser.permissions();
        requireModeration(pc);
        List<Integer> ids = topicIds == null ? List.of() : topicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return done(model, returnUrl, "Moderation.SelectTopics");
        }
        List<Topic> selected = requireScope(pc, ids);
        ModerationService.Moderator moderator = ModerationService.Moderator.of(currentUser.get(), reason);

        if (request.getParameter("topicRemove") != null) {
            require(pc, SecurityConstants.PERM_MODERATION_POST_REMOVE);
            service.deleteTopics(ids, moderator);
        } else if (request.getParameter("topicMove") != null) {
            require(pc, SecurityConstants.PERM_MODERATION_TOPIC_MOVE);
            model.addAttribute("topics", ids.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
            model.addAttribute("topicList", selected);
            model.addAttribute("forumId", selected.get(0).getForumId());
            model.addAttribute("categories", forumService.visibleCategories(pc));
            model.addAttribute("returnUrl", safeReturnUrl(returnUrl));
            return "moderation_move";
        } else if (request.getParameter("topicLock") != null) {
            require(pc, SecurityConstants.PERM_MODERATION_TOPIC_LOCK_UNLOCK);
            service.lock(ids, moderator);
        } else if (request.getParameter("topicUnlock") != null) {
            require(pc, SecurityConstants.PERM_MODERATION_TOPIC_LOCK_UNLOCK);
            service.unlock(ids, moderator);
        } else {
            throw new ForbiddenException("Moderation.Denied");
        }
        return done(model, returnUrl, "Moderation.ModerationDone");
    }

    @PostMapping({"/moderation/moveTopic.page", "/moderation/moveTopic"})
    public String moveTopic(@RequestParam("topics") String topicsParam,
                            @RequestParam(name = "to_forum", required = false) Integer toForum,
                            @RequestParam(name = "returnUrl", required = false) String returnUrl,
                            @RequestParam(name = "log_description", required = false) String reason, Model model) {
        PermissionControl pc = currentUser.permissions();
        requireModeration(pc);
        require(pc, SecurityConstants.PERM_MODERATION_TOPIC_MOVE);
        List<Integer> ids = Arrays.stream(topicsParam.split(",")).map(String::trim).filter(s -> s.matches("\\d+"))
            .map(Integer::valueOf).distinct().toList();
        Forum target = toForum == null ? null : forums.findById(toForum).orElse(null);
        if (target == null || !forumService.isForumAccessible(pc, target)) {
            return done(model, returnUrl, "Moderation.InvalidForum");
        }
        List<Topic> selected = requireScope(pc, ids);
        ModerationService.Moderator moderator = ModerationService.Moderator.of(currentUser.get(), reason);
        Map<Integer, List<Integer>> byForum = new LinkedHashMap<>();
        for (Topic t : selected) {
            byForum.computeIfAbsent(t.getForumId(), k -> new ArrayList<>()).add(t.getId());
        }
        byForum.forEach((from, list) -> service.move(list, from, target.getId(), moderator));
        return done(model, returnUrl, "Moderation.ModerationDone");
    }

    @GetMapping({"/moderation/moderationDone.page", "/moderation/moderationDone"})
    public String moderationDone(@RequestParam(name = "returnUrl", required = false) String returnUrl, Model model) {
        return done(model, returnUrl, "Moderation.ModerationDone");
    }

    // ------------------------------------------------------------------ moderator panel of a forum

    @GetMapping({"/moderation/forum/{forumId}.page", "/moderation/forum/{start}/{forumId}.page"})
    public String forumPanel(@PathVariable(required = false) Integer start, @PathVariable int forumId, Model model) {
        PermissionControl pc = currentUser.permissions();
        Forum forum = forumService.requireAccessible(pc, forumId);
        if (!pc.isModerator(forumId)) {
            throw new ForbiddenException("Moderation.Denied");
        }
        int perPage = config.topicsPerPage();
        int s = Pagination.normalizeStart(start, perPage);
        Page<Topic> page = topics.findByForumId(forumId, PageRequest.of(s / Math.max(perPage, 1), Math.max(perPage, 1),
            Sort.by(Sort.Direction.DESC, "lastPostTime", "_id")));
        model.addAttribute("forum", forum);
        model.addAttribute("topics", page.getContent());
        model.addAttribute("pagination", new Pagination(s, perPage, page.getTotalElements(), "/moderation/forum", String.valueOf(forumId)));
        model.addAttribute("pendingCount", service.pendingPosts(forumId).size());
        model.addAttribute("returnUrl", "/moderation/forum/" + (s > 0 ? s + "/" : "") + forumId + ".page");
        return "moderation_forum";
    }

    // ------------------------------------------------------------------ activity log

    @GetMapping({"/moderation/showActivityLog.page", "/moderation/showActivityLog/{start}.page"})
    public String showActivityLog(@PathVariable(required = false) Integer start, Model model) {
        PermissionControl pc = currentUser.permissions();
        if (!pc.canAccess(SecurityConstants.PERM_MODERATION_LOG)) {
            throw new ForbiddenException("Moderation.Denied");
        }
        int perPage = config.topicsPerPage();
        int s = Pagination.normalizeStart(start, perPage);
        Page<ModerationLog> page = logs.findAllByOrderByDateDesc(PageRequest.of(s / Math.max(perPage, 1), Math.max(perPage, 1)));
        model.addAttribute("logs", page.getContent());
        model.addAttribute("canAccessFullModerationLog", pc.canAccess(SecurityConstants.PERM_FULL_MODERATION_LOG));
        model.addAttribute("pagination", new OffsetPagination(s, perPage, page.getTotalElements(),
            x -> x == 0 ? "/moderation/showActivityLog.page" : "/moderation/showActivityLog/" + x + ".page"));
        return "moderation_activity_log";
    }

    // ------------------------------------------------------------------ pending posts queue

    @GetMapping({"/moderation/pending.page", "/moderation/pending"})
    public String pending(Model model) {
        PermissionControl pc = currentUser.permissions();
        requireApprove(pc);
        Map<Integer, Long> counts = service.pendingCountByForum();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Forum f : forumService.visibleForums(pc)) {
            if (pc.isModerator(f.getId())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("forum", f);
                row.put("pending", counts.getOrDefault(f.getId(), 0L));
                rows.add(row);
            }
        }
        model.addAttribute("rows", rows);
        return "moderation_pending";
    }

    @GetMapping({"/moderation/pendingView/{forumId}.page"})
    public String pendingView(@PathVariable int forumId, Model model) {
        PermissionControl pc = currentUser.permissions();
        requireApprove(pc);
        Forum forum = requireModeratedForum(pc, forumId);
        Map<Integer, List<Post>> byTopic = new LinkedHashMap<>();
        for (Post p : service.pendingPosts(forumId)) {
            byTopic.computeIfAbsent(p.getTopicId(), k -> new ArrayList<>()).add(p);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        byTopic.forEach((topicId, list) -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("topic", topics.findById(topicId).orElse(null));
            g.put("posts", list);
            groups.add(g);
        });
        model.addAttribute("forum", forum);
        model.addAttribute("groups", groups);
        return "moderation_pending_posts";
    }

    @PostMapping({"/moderation/pendingSave.page", "/moderation/pendingSave"})
    public String pendingSave(@RequestParam("forum_id") int forumId, HttpServletRequest request) {
        PermissionControl pc = currentUser.permissions();
        requireApprove(pc);
        requireModeratedForum(pc, forumId);
        ModerationService.Moderator moderator = ModerationService.Moderator.of(currentUser.get(),
            request.getParameter("log_description"));
        for (Post p : service.pendingPosts(forumId)) {
            String status = request.getParameter("status_" + p.getId());
            if ("aprove".equals(status)) {
                service.approve(p.getId(), moderator);
            } else if ("reject".equals(status)) {
                service.reject(p.getId(), moderator);
            }
        }
        return "redirect:/moderation/pendingView/" + forumId + ".page";
    }

    // ------------------------------------------------------------------ helpers

    private String done(Model model, String returnUrl, String messageKey) {
        String url = safeReturnUrl(returnUrl);
        String contextPath = ServletUriComponentsBuilder.fromCurrentContextPath().build().getPath();
        model.addAttribute("returnUrl", url);
        model.addAttribute("messageKey", messageKey);
        model.addAttribute("doneHtml", i18n.getMessage("Moderation.ModerationDone",
            HtmlUtils.htmlEscape((contextPath == null ? "" : contextPath) + url)));
        return "moderation_done";
    }

    /** Only local paths are accepted to avoid open redirects. */
    static String safeReturnUrl(String url) {
        if (url == null || !url.startsWith("/") || url.startsWith("//") || url.contains("\\") || url.contains("\r")
            || url.contains("\n")) {
            return "/forums/list.page";
        }
        return url;
    }

    private static void requireModeration(PermissionControl pc) {
        require(pc, SecurityConstants.PERM_MODERATION);
    }

    private static void requireApprove(PermissionControl pc) {
        requireModeration(pc);
        require(pc, SecurityConstants.PERM_MODERATION_APPROVE_MESSAGES);
    }

    private static void require(PermissionControl pc, String role) {
        if (!pc.canAccess(role)) {
            throw new ForbiddenException("Moderation.Denied");
        }
    }

    private Forum requireModeratedForum(PermissionControl pc, int forumId) {
        Forum forum = forums.findById(forumId).orElseThrow(() -> new NotFoundException("ForumListing.forumNotFound"));
        if (!pc.isModerator(forumId)) {
            throw new ForbiddenException("Moderation.Denied");
        }
        return forum;
    }

    private List<Topic> requireScope(PermissionControl pc, List<Integer> ids) {
        List<Topic> found = new ArrayList<>();
        topics.findAllById(ids).forEach(found::add);
        if (found.isEmpty()) {
            throw new NotFoundException("PostShow.TopicNotFound");
        }
        for (Topic t : found) {
            if (!pc.isModerator(t.getForumId())) {
                throw new ForbiddenException("Moderation.Denied");
            }
        }
        return found;
    }
}
