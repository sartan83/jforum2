package net.jforum.post;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.jforum.attachment.AttachmentException;
import net.jforum.attachment.AttachmentService;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Attachment;
import net.jforum.domain.Forum;
import net.jforum.domain.Poll;
import net.jforum.domain.PollOption;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicStatus;
import net.jforum.domain.User;
import net.jforum.forum.ForumService;
import net.jforum.i18n.I18n;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.security.CurrentUser;
import net.jforum.security.PermissionControl;
import net.jforum.security.SecurityConstants;
import net.jforum.support.Ids;
import net.jforum.support.Pagination;
import net.jforum.topic.ReadTracker;
import net.jforum.topic.TopicService;
import net.jforum.web.ForbiddenException;
import net.jforum.web.NotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Port of legacy PostAction (module "posts"), same friendly URLs as urlPattern.properties. */
@Controller
public class PostController {

    private static final String LOGIN = "redirect:/user/login.page";
    private static final String LAST_POST_TIME = "lastPostTime";

    private final ForumService forumService;
    private final ForumRepository forums;
    private final TopicRepository topics;
    private final PostRepository posts;
    private final PostService postService;
    private final TopicService topicService;
    private final CurrentUser currentUser;
    private final ForumConfig config;
    private final AttachmentService attachments;
    private final I18n i18n;

    public PostController(ForumService forumService, ForumRepository forums, TopicRepository topics, PostRepository posts,
                          PostService postService, TopicService topicService, CurrentUser currentUser, ForumConfig config,
                          AttachmentService attachments, I18n i18n) {
        this.attachments = attachments;
        this.i18n = i18n;
        this.forumService = forumService;
        this.forums = forums;
        this.topics = topics;
        this.posts = posts;
        this.postService = postService;
        this.topicService = topicService;
        this.currentUser = currentUser;
        this.config = config;
    }

    // ---- helpers ----

    private Topic requireTopic(String id) {
        Integer topicId = Ids.parse(id);
        return topicId == null ? null : topics.findById(topicId).orElse(null);
    }

    private Topic topicOrNotFound(String id) {
        Topic t = requireTopic(id);
        if (t == null) {
            throw new NotFoundException("PostShow.TopicNotFound");
        }
        return t;
    }

    private Post postOrNotFound(String id) {
        Integer postId = Ids.parse(id);
        return Optional.ofNullable(postId).flatMap(posts::findById)
            .orElseThrow(() -> new NotFoundException("PostShow.PostNotFound"));
    }

    private Forum forumOf(Integer forumId) {
        return Optional.ofNullable(forumId).flatMap(forums::findById)
            .orElseThrow(() -> new NotFoundException("ForumListing.forumNotFound"));
    }

    /** Legacy TopicsCommon.isTopicAccessible: anonymous users go to the login page, others get a denied message. */
    private String accessGuard(Forum forum, String deniedKey) {
        if (forumService.isForumAccessible(currentUser.permissions(), forum)) {
            return null;
        }
        if (!currentUser.isLogged()) {
            return LOGIN;
        }
        throw new ForbiddenException(deniedKey);
    }

    static String notice(Model model, String key, String returnPath) {
        model.addAttribute("noticeKey", key);
        model.addAttribute("returnPath", returnPath);
        return "notice";
    }

    private static String topicPath(Topic t) {
        return "/posts/list/" + t.getId() + ".page";
    }

    private static boolean locked(Topic t) {
        return t != null && t.getStatus() == TopicStatus.LOCKED;
    }

    /** Legacy insert/insertSave checks: access, anonymousPost, isForumReadonly, reply-only, locked topic. */
    private String postingGuard(Forum forum, Topic topic, Model model) {
        String denied = accessGuard(forum, "ForumListing.denied");
        if (denied != null) {
            return denied;
        }
        PermissionControl pc = currentUser.permissions();
        int forumId = forum.getId();
        if (!currentUser.isLogged() && !pc.canAccess(SecurityConstants.PERM_ANONYMOUS_POST, forumId)) {
            return LOGIN;
        }
        if (!pc.canAccess(SecurityConstants.PERM_READ_ONLY_FORUMS, forumId)) {
            return "redirect:" + (topic != null ? topicPath(topic) : "/forums/show/" + forumId + ".page");
        }
        if (topic == null && !pc.canAccess(SecurityConstants.PERM_REPLY_ONLY, forumId)) {
            return notice(model, "PostShow.replyOnly", "/forums/show/" + forumId + ".page");
        }
        if (locked(topic)) {
            return notice(model, "PostShow.topicLocked", topicPath(topic));
        }
        return null;
    }

    private String redirectToPost(int topicId, int postId) {
        int per = config.postsPerPage();
        long before = postService.countPostsBefore(topicId, postId);
        int start = (int) (before / per) * per;
        return "redirect:/posts/list/" + (start > 0 ? start + "/" : "") + topicId + ".page#" + postId;
    }

    // ---- topic view ----

    @GetMapping({"/posts/list/{topicId}.page", "/posts/list/{start}/{topicId}.page"})
    public String list(@PathVariable String topicId, @PathVariable(required = false) Integer start,
                       @RequestParam(name = "viewResults", required = false) String viewResults,
                       Model model, HttpServletRequest request, HttpSession session) {
        Topic topic = topicOrNotFound(topicId);
        Forum forum = forumOf(topic.getForumId());
        String denied = accessGuard(forum, "PostShow.denied");
        if (denied != null) {
            return denied;
        }
        PermissionControl pc = currentUser.permissions();
        boolean logged = currentUser.isLogged();
        int uid = currentUser.getId();
        int per = config.postsPerPage();
        int s = Pagination.normalizeStart(start, per);

        topicService.incrementViews(topic.getId());
        topic.setViews(topic.getViews() + 1);
        List<Post> page = postService.topicPosts(topic.getId(), uid, s, per);
        long total = postService.countTopicPosts(topic.getId(), uid);
        if (logged) {
            ReadTracker.markTopicRead(session, topic.getId());
            ReadTracker.flush(session);
            topicService.markWatchRead(topic.getId(), uid);
        }

        Poll poll = topic.getPoll();
        boolean canVote = poll != null && logged && pc.canAccess(SecurityConstants.PERM_VOTE) && !locked(topic)
            && PostService.isPollOpen(poll) && !PostService.hasVoted(poll, uid);
        model.addAttribute("topic", topic);
        model.addAttribute("forum", forum);
        model.addAttribute("posts", postService.views(page, uid, logged, pc, request.getContextPath()));
        Map<String, List<Attachment>> attachmentsByPost = new HashMap<>();
        if (attachments.canDownload(pc, forum.getId())) {
            page.stream().filter(Post::isHasAttachments)
                .forEach(p -> attachmentsByPost.put(String.valueOf(p.getId()), attachments.forPost(p.getId())));
        }
        model.addAttribute("attachmentsByPost", attachmentsByPost);
        model.addAttribute("pagination", new Pagination(s, per, total, "/posts/list", String.valueOf(topic.getId())));
        model.addAttribute("start", s);
        model.addAttribute("locked", locked(topic));
        model.addAttribute("watching", logged && topicService.isWatching(topic.getId(), uid));
        model.addAttribute("canRemove", pc.canAccess(SecurityConstants.PERM_MODERATION_POST_REMOVE));
        model.addAttribute("readonly", !pc.canAccess(SecurityConstants.PERM_READ_ONLY_FORUMS, forum.getId()));
        model.addAttribute("isModerator", pc.isModerator(forum.getId()));
        model.addAttribute("poll", poll);
        model.addAttribute("pollOptions", poll == null ? List.of() : pollRows(poll));
        model.addAttribute("pollTotal", PostService.totalVotes(poll));
        model.addAttribute("canVoteOnPoll", canVote && viewResults == null);
        return "posts_list";
    }

    private static List<PollRow> pollRows(Poll poll) {
        int total = PostService.totalVotes(poll);
        List<PollRow> rows = new ArrayList<>();
        for (PollOption o : poll.getOptions()) {
            rows.add(new PollRow(o.getId(), o.getText(), o.getVoteCount(), total == 0 ? 0 : Math.round(o.getVoteCount() * 100f / total)));
        }
        return rows;
    }

    /** Poll option with its percentage (plain getters for FreeMarker). */
    public static class PollRow {
        private final int id;
        private final String text;
        private final int votes;
        private final int percent;

        PollRow(int id, String text, int votes, int percent) {
            this.id = id;
            this.text = text;
            this.votes = votes;
            this.percent = percent;
        }

        public int getId() { return id; }
        public String getText() { return text; }
        public int getVotes() { return votes; }
        public int getPercent() { return percent; }
    }

    @GetMapping("/posts/preList/{topicId}/{postId}.page")
    public String preList(@PathVariable String topicId, @PathVariable String postId) {
        Topic topic = topicOrNotFound(topicId);
        Integer pid = Ids.parse(postId);
        if (pid == null) {
            return "redirect:" + topicPath(topic);
        }
        return redirectToPost(topic.getId(), pid);
    }

    // ---- forms ----

    private PostCommand defaults() {
        User u = currentUser.get();
        PostCommand c = new PostCommand();
        c.setAttachSig(u.isAttachSignature());
        c.setNotify(currentUser.isLogged() && u.isNotifyOnMessagesEnabled());
        c.setDisableHtml(!u.isHtmlEnabled());
        c.setDisableBbCode(!u.isBbCodeEnabled());
        c.setDisableSmilies(!u.isSmiliesEnabled());
        return c;
    }

    private String form(Model model, Forum forum, Topic topic, Post editing, PostCommand c) {
        PermissionControl pc = currentUser.permissions();
        boolean firstPost = topic == null || (editing != null && Objects.equals(topic.getFirstPostId(), editing.getId()));
        c.setForumId(forum.getId());
        c.setTopicId(topic == null ? null : topic.getId());
        c.setPostId(editing == null ? null : editing.getId());
        model.addAttribute("forum", forum);
        model.addAttribute("topic", topic);
        model.addAttribute("command", c);
        model.addAttribute("editing", editing != null);
        model.addAttribute("action", editing != null ? "editSave" : "insertSave");
        model.addAttribute("htmlAllowed", pc.canAccess(SecurityConstants.PERM_HTML_DISABLED, forum.getId()));
        model.addAttribute("canCreateStickyOrAnnouncementTopics",
            firstPost && pc.canAccess(SecurityConstants.PERM_CREATE_STICKY_ANNOUNCEMENT_TOPICS));
        model.addAttribute("canCreatePolls", firstPost && pc.canAccess(SecurityConstants.PERM_CREATE_POLL));
        List<String> options = new ArrayList<>(c.getPollOptions());
        while (options.size() < Math.max(2, c.getPollOptions().size() + 1)) {
            options.add("");
        }
        model.addAttribute("pollOptionFields", options);
        int existing = editing == null ? 0 : attachments.forPost(editing.getId()).size();
        model.addAttribute("canAttach", attachments.canAttach(pc, forum.getId()));
        model.addAttribute("maxAttachments", Math.max(0, attachments.maxAttachmentsPerPost() - existing));
        model.addAttribute("existingAttachments", existing);
        return "post_form";
    }

    @GetMapping("/posts/insert/{forumId}.page")
    public String insert(@PathVariable String forumId, Model model) {
        Forum forum = forumOf(Ids.parse(forumId));
        String guard = postingGuard(forum, null, model);
        return guard != null ? guard : form(model, forum, null, null, defaults());
    }

    @GetMapping({"/posts/reply/{topicId}.page", "/posts/reply/{start}/{topicId}.page"})
    public String reply(@PathVariable String topicId, Model model) {
        Topic topic = topicOrNotFound(topicId);
        Forum forum = forumOf(topic.getForumId());
        String guard = postingGuard(forum, topic, model);
        return guard != null ? guard : form(model, forum, topic, null, defaults());
    }

    @GetMapping({"/posts/quote/{postId}.page", "/posts/quote/{start}/{postId}.page"})
    public String quote(@PathVariable String postId, Model model) {
        Post post = postOrNotFound(postId);
        if (post.isModerate()) {
            return notice(model, "PostShow.notModeratedYet", "/forums/list.page");
        }
        Topic topic = topicOrNotFound(String.valueOf(post.getTopicId()));
        Forum forum = forumOf(topic.getForumId());
        String guard = postingGuard(forum, topic, model);
        if (guard != null) {
            return guard;
        }
        PostCommand c = defaults();
        c.setMessage("[quote=" + post.getUsername() + "]" + post.getText() + "[/quote]\n");
        return form(model, forum, topic, null, c);
    }

    private boolean canEdit(Post post) {
        return currentUser.isLogged() && (Objects.equals(post.getUserId(), currentUser.getId())
            || currentUser.canAccess(SecurityConstants.PERM_MODERATION_POST_EDIT));
    }

    /** Legacy edit/editSave checks; returns a view when editing is not allowed. */
    private String editGuard(Post post, Topic topic, Forum forum, Model model) {
        String denied = accessGuard(forum, "PostShow.denied");
        if (denied != null) {
            return denied;
        }
        if (!canEdit(post)) {
            throw new ForbiddenException("CannotEditPost");
        }
        if (locked(topic) && !currentUser.canAccess(SecurityConstants.PERM_MODERATION_POST_EDIT)) {
            return notice(model, "PostShow.topicLocked", topicPath(topic));
        }
        return null;
    }

    @GetMapping({"/posts/edit/{postId}.page", "/posts/edit/{start}/{postId}.page", "/posts/edit.page"})
    public String edit(@PathVariable(required = false) String postId, @RequestParam(name = "post_id", required = false) String postIdParam,
                       Model model) {
        Post post = postOrNotFound(postId != null ? postId : postIdParam);
        Topic topic = topicOrNotFound(String.valueOf(post.getTopicId()));
        Forum forum = forumOf(topic.getForumId());
        String guard = editGuard(post, topic, forum, model);
        if (guard != null) {
            return guard;
        }
        PostCommand c = new PostCommand();
        c.setSubject(post.getSubject());
        c.setMessage(post.getText());
        c.setDisableHtml(!post.isHtmlEnabled());
        c.setDisableBbCode(!post.isBbCodeEnabled());
        c.setDisableSmilies(!post.isSmiliesEnabled());
        c.setAttachSig(post.isSignatureEnabled());
        c.setNotify(topicService.isWatching(topic.getId(), currentUser.getId()));
        if (Objects.equals(topic.getFirstPostId(), post.getId())) {
            if (c.getSubject().isBlank()) {
                c.setSubject(topic.getTitle());
            }
            c.setTopicType(topic.getType());
            if (topic.getPoll() != null) {
                c.setPollLabel(topic.getPoll().getLabel());
                c.setPollLength(topic.getPoll().getLength());
                c.setPollOptions(topic.getPoll().getOptions().stream().map(PollOption::getText).toList());
            }
        }
        return form(model, forum, topic, post, c);
    }

    private String validate(PostCommand c, boolean firstPost, HttpSession session) {
        if (c.getMessage().isBlank()) {
            return "PostForm.textEmpty";
        }
        if (firstPost && c.getSubject().isBlank()) {
            return "PostForm.subjectEmpty";
        }
        if (firstPost && c.hasPoll() && currentUser.canAccess(SecurityConstants.PERM_CREATE_POLL) && c.getPollOptions().size() < 2) {
            return "PostForm.needMorePollOptions";
        }
        return null;
    }

    private String formWithPreview(Model model, Forum forum, Topic topic, Post editing, PostCommand c, String error) {
        if (error != null) {
            model.addAttribute("errorKey", error);
        } else {
            model.addAttribute("previewHtml", postService.previewHtml(c, forum.getId(), currentUser.permissions()));
        }
        return form(model, forum, topic, editing, c);
    }

    private String attachmentError(Model model, Forum forum, Topic topic, Post editing, PostCommand c, AttachmentException e) {
        model.addAttribute("errorMessage", i18n.getMessage(e.getMessageKey(), e.getArgs()));
        return form(model, forum, topic, editing, c);
    }

    @PostMapping("/posts/insertSave.page")
    public String insertSave(HttpServletRequest request, HttpSession session, Model model) {
        PostCommand c = PostCommand.from(request);
        Topic topic = null;
        if (c.getTopicId() != null) {
            topic = topicOrNotFound(String.valueOf(c.getTopicId()));
        }
        Forum forum = forumOf(topic != null ? topic.getForumId() : c.getForumId());
        String guard = postingGuard(forum, topic, model);
        if (guard != null) {
            return guard;
        }
        String error = validate(c, topic == null, session);
        if (error == null && !currentUser.canAccess(SecurityConstants.PERM_MODERATION) && tooSoon(session)) {
            error = "PostForm.tooSoon";
        }
        if (error != null || request.getParameter("preview") != null) {
            return formWithPreview(model, forum, topic, null, c, error);
        }
        List<AttachmentService.Upload> uploads;
        try {
            uploads = attachments.validateRequest(request, currentUser.get(), forum.getId(), currentUser.permissions(), 0);
        } catch (AttachmentException e) {
            return attachmentError(model, forum, topic, null, c, e);
        }
        Post post = postService.insert(c, forum, topic, currentUser.get(), currentUser.isLogged(), currentUser.permissions(),
            request.getRemoteAddr());
        attachments.store(uploads, post, currentUser.get());
        session.setAttribute(LAST_POST_TIME, System.currentTimeMillis());
        if (post.isModerate()) {
            return "redirect:/posts/waitingModeration/" + post.getTopicId() + "/" + forum.getId() + ".page";
        }
        return redirectToPost(post.getTopicId(), post.getId());
    }

    private boolean tooSoon(HttpSession session) {
        Object last = session.getAttribute(LAST_POST_TIME);
        long delay = config.getInt("posts.new.delay", 0);
        return delay > 0 && last instanceof Long l && System.currentTimeMillis() - l < delay;
    }

    @PostMapping("/posts/editSave.page")
    public String editSave(HttpServletRequest request, HttpSession session, Model model) {
        PostCommand c = PostCommand.from(request);
        Post post = postOrNotFound(String.valueOf(c.getPostId()));
        Topic topic = topicOrNotFound(String.valueOf(post.getTopicId()));
        Forum forum = forumOf(topic.getForumId());
        String guard = editGuard(post, topic, forum, model);
        if (guard != null) {
            return guard;
        }
        String error = validate(c, Objects.equals(topic.getFirstPostId(), post.getId()), session);
        if (error != null || request.getParameter("preview") != null) {
            return formWithPreview(model, forum, topic, post, c, error);
        }
        List<AttachmentService.Upload> uploads;
        try {
            uploads = attachments.validateRequest(request, currentUser.get(), forum.getId(), currentUser.permissions(),
                attachments.forPost(post.getId()).size());
        } catch (AttachmentException e) {
            return attachmentError(model, forum, topic, post, c, e);
        }
        postService.edit(post, topic, c, currentUser.get(), currentUser.permissions());
        attachments.store(uploads, post, currentUser.get());
        return redirectToPost(topic.getId(), post.getId());
    }

    @PostMapping({"/posts/delete/{postId}.page", "/posts/delete.page"})
    public String delete(@PathVariable(required = false) String postId, @RequestParam(name = "post_id", required = false) String postIdParam) {
        if (!currentUser.canAccess(SecurityConstants.PERM_MODERATION_POST_REMOVE)) {
            throw new ForbiddenException("CannotRemovePost");
        }
        Post post = postOrNotFound(postId != null ? postId : postIdParam);
        Forum forum = forumOf(post.getForumId());
        String denied = accessGuard(forum, "PostShow.denied");
        if (denied != null) {
            return denied;
        }
        return postService.delete(post, currentUser.get())
            .map(t -> "redirect:" + topicPath(t))
            .orElse("redirect:/forums/show/" + forum.getId() + ".page");
    }

    // ---- polls ----

    @PostMapping("/posts/vote.page")
    public String vote(@RequestParam(name = "topic_id", required = false) String topicId,
                       @RequestParam(name = "poll_option", required = false) String option,
                       HttpServletRequest request, Model model) {
        Topic topic = topicOrNotFound(topicId);
        Forum forum = forumOf(topic.getForumId());
        String denied = accessGuard(forum, "PostShow.denied");
        if (denied != null) {
            return denied;
        }
        if (locked(topic)) {
            return notice(model, "PostShow.topicLocked", topicPath(topic));
        }
        if (!currentUser.isLogged()) {
            return LOGIN;
        }
        if (!currentUser.canAccess(SecurityConstants.PERM_VOTE)) {
            throw new ForbiddenException("PostShow.denied");
        }
        Integer optionId = Ids.parse(option);
        if (optionId != null) {
            postService.vote(topic, optionId, currentUser.getId(), request.getRemoteAddr());
        }
        return "redirect:" + topicPath(topic);
    }

    // ---- watches & moderation ----

    @GetMapping({"/posts/watch/{topicId}.page", "/posts/watch/{start}/{topicId}.page"})
    public String watch(@PathVariable String topicId, @PathVariable(required = false) Integer start) {
        Topic topic = topicOrNotFound(topicId);
        if (!currentUser.isLogged()) {
            return LOGIN;
        }
        String denied = accessGuard(forumOf(topic.getForumId()), "PostShow.denied");
        if (denied != null) {
            return denied;
        }
        topicService.watch(topic.getId(), currentUser.getId());
        return "redirect:/posts/list/" + (start != null && start > 0 ? start + "/" : "") + topic.getId() + ".page";
    }

    @GetMapping({"/posts/unwatch/{topicId}.page", "/posts/unwatch/{start}/{topicId}.page"})
    public String unwatch(@PathVariable String topicId, @PathVariable(required = false) Integer start, Model model) {
        Topic topic = topicOrNotFound(topicId);
        if (!currentUser.isLogged()) {
            return LOGIN;
        }
        topicService.unwatch(topic.getId(), currentUser.getId());
        return notice(model, "ForumBase.unwatched", "/posts/list/" + (start != null && start > 0 ? start + "/" : "") + topic.getId() + ".page");
    }

    @GetMapping("/posts/waitingModeration/{topicId}/{forumId}.page")
    public String waitingModeration(@PathVariable String topicId, @PathVariable String forumId, Model model) {
        Topic topic = requireTopic(topicId);
        String back = topic != null && !topic.isModerated() ? topicPath(topic) : "/forums/show/" + Ids.parse(forumId) + ".page";
        return notice(model, "PostShow.waitingModeration", back);
    }
}
