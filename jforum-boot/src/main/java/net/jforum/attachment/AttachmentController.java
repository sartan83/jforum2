package net.jforum.attachment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import net.jforum.domain.Attachment;
import net.jforum.domain.Forum;
import net.jforum.domain.Post;
import net.jforum.forum.ForumService;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.PostRepository;
import net.jforum.security.CurrentUser;
import net.jforum.security.PermissionControl;
import net.jforum.web.ForbiddenException;
import net.jforum.web.NotFoundException;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Legacy PostAction.downloadAttach plus a small page to manage the attachments of an existing post
 * (the post form itself belongs to the posting track and calls {@link AttachmentService}).
 */
@Controller
public class AttachmentController {

    private final AttachmentService service;
    private final PostRepository posts;
    private final ForumRepository forums;
    private final ForumService forumService;
    private final CurrentUser currentUser;
    private final MessageSource messages;

    public AttachmentController(AttachmentService service, PostRepository posts, ForumRepository forums,
                                ForumService forumService, CurrentUser currentUser, MessageSource messages) {
        this.service = service;
        this.posts = posts;
        this.forums = forums;
        this.forumService = forumService;
        this.currentUser = currentUser;
        this.messages = messages;
    }

    @GetMapping("/posts/downloadAttach/{attachId}.page")
    public ResponseEntity<?> download(@PathVariable int attachId) throws IOException {
        if (!currentUser.isLogged() && !service.anonymousDownloadAllowed()) {
            return ResponseEntity.status(302).location(ServletUriComponentsBuilder.fromCurrentContextPath().path("/user/login.page").build().toUri()).build();
        }
        Attachment a = service.find(attachId).orElseThrow(() -> new NotFoundException("Attachments.notFound"));
        Post post = a.getPostId() == null ? null : posts.findById(a.getPostId()).orElse(null);
        if (post == null) {
            throw new NotFoundException("Attachments.notFound");
        }
        PermissionControl pc = currentUser.permissions();
        Forum forum = forums.findById(post.getForumId()).orElseThrow(() -> new NotFoundException("Attachments.notFound"));
        if (!forumService.isForumAccessible(pc, forum) || !service.canDownload(pc, forum.getId())
            || (post.isModerate() && !pc.isModerator(forum.getId()))) {
            throw new ForbiddenException("Attachments.featureDisabled");
        }
        GridFsResource resource = service.content(a);
        if (resource == null || !resource.exists()) {
            throw new NotFoundException("Attachments.notFound");
        }
        service.incrementDownloadCount(attachId);

        MediaType type = parseType(a.getMimetype());
        String sub = type.getSubtype().toLowerCase(Locale.ROOT);
        boolean active = sub.contains("html") || sub.contains("xml") || sub.contains("svg") || sub.contains("javascript");
        ContentDisposition cd = (service.isPhysicalDownload(a) || active ? ContentDisposition.attachment() : ContentDisposition.inline())
            .filename(a.getRealFilename(), asciiOnly(a.getRealFilename()) ? null : StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
            .contentType(active ? MediaType.APPLICATION_OCTET_STREAM : type)
            .contentLength(a.getFilesize())
            .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(new InputStreamResource(resource.getInputStream()));
    }

    @GetMapping("/attachments/manage/{postId}.page")
    public String manage(@PathVariable int postId, Model model) {
        Post post = requireManageable(postId);
        model.addAttribute("post", post);
        model.addAttribute("forum", forums.findById(post.getForumId()).orElse(null));
        model.addAttribute("attachments", service.forPost(postId));
        model.addAttribute("maxAttachments", service.maxAttachmentsPerPost());
        long quota = service.quotaBytes(currentUser.get());
        model.addAttribute("quotaKb", quota < 0 ? null : quota / 1024);
        model.addAttribute("canAttach", service.canAttach(currentUser.permissions(), post.getForumId()));
        return "attach_manage";
    }

    @PostMapping("/attachments/upload/{postId}.page")
    public String upload(@PathVariable int postId, @RequestParam(name = "file", required = false) List<MultipartFile> files,
                         @RequestParam(name = "comment", required = false) List<String> comments, RedirectAttributes redirect) {
        Post post = requireManageable(postId);
        try {
            service.attach(post, files == null ? List.of() : files, comments, currentUser.get(), currentUser.permissions());
        } catch (AttachmentException e) {
            redirect.addFlashAttribute("error", messages.getMessage(e.getMessageKey(), e.getArgs(), e.getMessageKey(),
                LocaleContextHolder.getLocale()));
        }
        return "redirect:/attachments/manage/" + postId + ".page";
    }

    @PostMapping("/attachments/delete/{attachId}.page")
    public String delete(@PathVariable int attachId) {
        Attachment a = service.find(attachId).orElseThrow(() -> new NotFoundException("Attachments.notFound"));
        Post post = requireManageable(a.getPostId());
        service.delete(attachId);
        return "redirect:/attachments/manage/" + post.getId() + ".page";
    }

    /** Post author (logged in) or a moderator of the forum, like legacy post editing. */
    private Post requireManageable(Integer postId) {
        Post post = postId == null ? null : posts.findById(postId).orElse(null);
        if (post == null) {
            throw new NotFoundException("PostShow.PostNotFound");
        }
        PermissionControl pc = currentUser.permissions();
        forumService.requireAccessible(pc, post.getForumId());
        boolean owner = currentUser.isLogged() && post.getUserId() != null && post.getUserId() == currentUser.getId();
        if (!owner && !pc.isModerator(post.getForumId())) {
            throw new ForbiddenException("Moderation.Denied");
        }
        return post;
    }

    private static MediaType parseType(String mime) {
        try {
            return mime == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mime);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static boolean asciiOnly(String s) {
        return s.chars().allMatch(c -> c >= 0x20 && c < 0x7f);
    }
}
