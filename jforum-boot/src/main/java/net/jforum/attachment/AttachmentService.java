package net.jforum.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Attachment;
import net.jforum.domain.AttachmentExtension;
import net.jforum.domain.AttachmentExtensionGroup;
import net.jforum.domain.Post;
import net.jforum.domain.QuotaLimit;
import net.jforum.domain.Topic;
import net.jforum.domain.User;
import net.jforum.repository.AttachmentExtensionGroupRepository;
import net.jforum.repository.AttachmentExtensionRepository;
import net.jforum.repository.AttachmentRepository;
import net.jforum.repository.QuotaLimitRepository;
import net.jforum.security.PermissionControl;
import net.jforum.security.SecurityConstants;
import net.jforum.support.SequenceService;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

/**
 * Attachment handling ported from legacy AttachmentCommon (preProcess / insertAttachments / editAttachments)
 * and PostAction.downloadAttach. Bytes live in GridFS, metadata in the {@code attachments} collection.
 *
 * <p>Post form integration (Track 1): after the post has been saved call
 * {@link #attachFromRequest(HttpServletRequest, Post, User, PermissionControl)} on insert and
 * {@link #editFromRequest(HttpServletRequest, Post, User, PermissionControl)} on edit. They read the legacy
 * form fields {@code total_files}, {@code file_N}, {@code comment_N}, {@code delete_attach} and
 * {@code edit_attach_ids} / {@code edit_comment_<id>}.
 */
@Service
public class AttachmentService {

    static final String DENY_ALL = "*";
    public static final int MAX_COMMENT = 254;

    private final AttachmentRepository attachments;
    private final AttachmentExtensionRepository extensions;
    private final AttachmentExtensionGroupRepository extensionGroups;
    private final QuotaLimitRepository quotas;
    private final GridFsTemplate gridFs;
    private final MongoTemplate mongo;
    private final SequenceService sequences;
    private final ForumConfig config;

    public AttachmentService(AttachmentRepository attachments, AttachmentExtensionRepository extensions,
                             AttachmentExtensionGroupRepository extensionGroups, QuotaLimitRepository quotas,
                             GridFsTemplate gridFs, MongoTemplate mongo, SequenceService sequences, ForumConfig config) {
        this.attachments = attachments;
        this.extensions = extensions;
        this.extensionGroups = extensionGroups;
        this.quotas = quotas;
        this.gridFs = gridFs;
        this.mongo = mongo;
        this.sequences = sequences;
        this.config = config;
    }

    /** A validated upload waiting to be stored. */
    public record Upload(MultipartFile file, String comment, String extension, Integer extensionId) {
    }

    // ------------------------------------------------------------------ permissions

    /** Legacy perm_attachments_enabled (forum scoped): may the user upload in this forum? */
    public boolean canAttach(PermissionControl pc, int forumId) {
        return pc.canAccess(SecurityConstants.PERM_ATTACHMENTS_ENABLED, forumId);
    }

    /** Legacy PostAction.downloadAttach / getAttachments check. */
    public boolean canDownload(PermissionControl pc, int forumId) {
        return pc.canAccess(SecurityConstants.PERM_ATTACHMENTS_ENABLED, forumId)
            || pc.canAccess(SecurityConstants.PERM_ATTACHMENTS_DOWNLOAD)
            || pc.canAccess(SecurityConstants.PERM_ATTACHMENTS_DOWNLOAD, forumId);
    }

    public int maxAttachmentsPerPost() {
        return config.getInt(ConfigKeys.ATTACHMENTS_MAX_POST, 3);
    }

    public boolean anonymousDownloadAllowed() {
        return !"false".equalsIgnoreCase(config.get("attachments.anonymous", "true").trim());
    }

    /** Largest quota (bytes) among the user's groups, or -1 when no limit applies (legacy getQuotaLimit). */
    public long quotaBytes(User user) {
        Set<Integer> groups = new HashSet<>(user.getGroupIds());
        long max = -1;
        for (QuotaLimit q : quotas.findAll()) {
            if (q.getGroupIds().stream().anyMatch(groups::contains)) {
                max = Math.max(max, sizeInBytes(q));
            }
        }
        return max;
    }

    static long sizeInBytes(QuotaLimit q) {
        return q.getType() == 1 ? q.getSize() * 1024L : q.getSize() * 1024L * 1024L;
    }

    // ------------------------------------------------------------------ upload

    /** Legacy AttachmentCommon.preProcess: validates count, file names, extensions, comments and quota. */
    public List<Upload> validate(List<MultipartFile> files, List<String> comments, User user, int forumId,
                                 PermissionControl pc, int alreadyAttached) {
        List<Upload> result = new ArrayList<>();
        List<MultipartFile> nonEmpty = new ArrayList<>();
        List<String> nonEmptyComments = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            if (f != null && !f.isEmpty() && f.getOriginalFilename() != null && !f.getOriginalFilename().isBlank()) {
                nonEmpty.add(f);
                nonEmptyComments.add(comments != null && i < comments.size() ? comments.get(i) : null);
            }
        }
        if (nonEmpty.isEmpty()) {
            return result;
        }
        if (!canAttach(pc, forumId)) {
            throw new AttachmentException("Attachments.featureDisabled");
        }
        int max = maxAttachmentsPerPost();
        if (nonEmpty.size() + alreadyAttached > max) {
            throw new AttachmentException("Attachments.maxToAttach", max);
        }
        Map<String, Boolean> rules = extensionRules();
        long totalSize = 0;
        for (int i = 0; i < nonEmpty.size(); i++) {
            MultipartFile f = nonEmpty.get(i);
            String name = f.getOriginalFilename();
            if (name.indexOf('\0') > -1) {
                throw new AttachmentException("Attachments.badExtension", "\\0");
            }
            String ext = extensionOf(name);
            boolean contains = rules.containsKey(ext);
            boolean allowed = (!rules.containsKey(DENY_ALL) && !contains) || (contains && rules.get(ext));
            if (!allowed) {
                throw new AttachmentException("Attachments.badExtension", ext);
            }
            String comment = nonEmptyComments.get(i);
            if (comment != null && comment.length() > MAX_COMMENT) {
                throw new AttachmentException("Comment too long.");
            }
            totalSize += f.getSize();
            Integer extId = extensions.findByExtensionIgnoreCase(ext).map(AttachmentExtension::getId).orElse(null);
            result.add(new Upload(f, comment, ext, extId));
        }
        long quota = quotaBytes(user);
        if (quota >= 0 && totalSize > quota) {
            throw new AttachmentException("Attachments.tooBig", quota / 1024, totalSize / 1024);
        }
        return result;
    }

    /** Legacy AttachmentCommon.insertAttachments: stores validated uploads for an already saved post. */
    public List<Attachment> store(List<Upload> uploads, Post post, User user) {
        List<Attachment> saved = new ArrayList<>();
        for (Upload u : uploads) {
            MultipartFile f = u.file();
            String contentType = f.getContentType() == null ? "application/octet-stream" : f.getContentType();
            ObjectId fileId;
            try (InputStream in = f.getInputStream()) {
                fileId = gridFs.store(in, f.getOriginalFilename(), contentType,
                    new Document("postId", post.getId()).append("userId", user.getId()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Attachment a = new Attachment();
            a.setId(sequences.next("attachments"));
            a.setPostId(post.getId());
            a.setUserId(user.getId());
            a.setGridFsId(fileId.toHexString());
            a.setPhysicalFilename(fileId.toHexString() + (u.extension().isEmpty() ? "" : "." + u.extension()));
            a.setRealFilename(f.getOriginalFilename());
            a.setDescription(u.comment());
            a.setMimetype(contentType);
            a.setFilesize(f.getSize());
            a.setUploadTime(Instant.now());
            a.setExtensionId(u.extensionId());
            saved.add(attachments.save(a));
        }
        if (!saved.isEmpty()) {
            markHasAttachments(post, true);
        }
        return saved;
    }

    /** Validate and store in one step. */
    public List<Attachment> attach(Post post, List<MultipartFile> files, List<String> comments, User user, PermissionControl pc) {
        int existing = attachments.findByPostId(post.getId()).size();
        return store(validate(files, comments, user, post.getForumId(), pc, existing), post, user);
    }

    /** Reads the legacy {@code file_N} / {@code comment_N} fields of a multipart post form. */
    public List<Attachment> attachFromRequest(HttpServletRequest request, Post post, User user, PermissionControl pc) {
        int existing = attachments.findByPostId(post.getId()).size();
        return store(validateRequest(request, user, post.getForumId(), pc, existing), post, user);
    }

    /** Validates the {@code file_N} / {@code comment_N} uploads of a post form before the post is saved. */
    public List<Upload> validateRequest(HttpServletRequest request, User user, int forumId, PermissionControl pc, int existing) {
        MultipartHttpServletRequest multipart = multipartOf(request);
        if (multipart == null) {
            return List.of();
        }
        int total = parseInt(request.getParameter("total_files"), -1);
        List<MultipartFile> files = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        int limit = total >= 0 ? total : multipart.getFileMap().size();
        for (int i = 0; i < Math.max(limit, maxAttachmentsPerPost()); i++) {
            MultipartFile f = multipart.getFile("file_" + i);
            if (f != null) {
                files.add(f);
                comments.add(request.getParameter("comment_" + i));
            }
        }
        return validate(files, comments, user, forumId, pc, existing);
    }

    /** Filters (security, session) may wrap the multipart request the DispatcherServlet created. */
    private static MultipartHttpServletRequest multipartOf(HttpServletRequest request) {
        MultipartHttpServletRequest multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
        if (multipart == null && request.getContentType() != null
            && request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/")) {
            multipart = new StandardMultipartHttpServletRequest(request);
        }
        return multipart;
    }

    /** Legacy AttachmentCommon.editAttachments: delete_attach (comma separated ids) and edit_comment_ID fields. */
    public void editFromRequest(HttpServletRequest request, Post post, User user, PermissionControl pc) {
        String delete = request.getParameter("delete_attach");
        if (delete != null && !delete.isBlank()) {
            for (String s : delete.split(",")) {
                int id = parseInt(s.trim(), -1);
                attachments.findById(id).filter(a -> post.getId().equals(a.getPostId())).ifPresent(this::remove);
            }
        }
        String edit = request.getParameter("edit_attach_ids");
        if (edit != null && !edit.isBlank()) {
            for (String s : edit.split(",")) {
                int id = parseInt(s.trim(), -1);
                String comment = request.getParameter("edit_comment_" + id);
                attachments.findById(id).filter(a -> post.getId().equals(a.getPostId())).ifPresent(a -> {
                    a.setDescription(comment == null ? null : comment.substring(0, Math.min(comment.length(), MAX_COMMENT)));
                    attachments.save(a);
                });
            }
        }
        attachFromRequest(request, post, user, pc);
        markHasAttachments(post, !attachments.findByPostId(post.getId()).isEmpty());
    }

    // ------------------------------------------------------------------ read / delete

    public List<Attachment> forPost(int postId) {
        return attachments.findByPostId(postId);
    }

    public Optional<Attachment> find(int attachId) {
        return attachments.findById(attachId);
    }

    public GridFsResource content(Attachment a) {
        return gridFs.getResource(gridFs.findOne(Query.query(Criteria.where("_id").is(new ObjectId(a.getGridFsId())))));
    }

    /** Legacy download_mode: 1 inline, 2 physical (forced download). */
    public boolean isPhysicalDownload(Attachment a) {
        if (a.getExtensionId() == null) {
            return true;
        }
        return extensions.findById(a.getExtensionId())
            .flatMap(e -> e.getExtensionGroupId() == null ? Optional.empty() : extensionGroups.findById(e.getExtensionGroupId()))
            .map(g -> g.getDownloadMode() == 2)
            .orElse(true);
    }

    public void incrementDownloadCount(int attachId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(attachId)), new Update().inc("downloadCount", 1), Attachment.class);
    }

    public void delete(int attachId) {
        attachments.findById(attachId).ifPresent(a -> {
            remove(a);
            if (a.getPostId() != null && attachments.findByPostId(a.getPostId()).isEmpty()) {
                mongo.updateFirst(Query.query(Criteria.where("_id").is(a.getPostId())), Update.update("hasAttachments", false), Post.class);
            }
        });
    }

    public void deleteByPost(int postId) {
        attachments.findByPostId(postId).forEach(this::remove);
    }

    private void remove(Attachment a) {
        if (a.getGridFsId() != null && ObjectId.isValid(a.getGridFsId())) {
            gridFs.delete(Query.query(Criteria.where("_id").is(new ObjectId(a.getGridFsId()))));
        }
        attachments.delete(a);
    }

    // ------------------------------------------------------------------ helpers

    private void markHasAttachments(Post post, boolean value) {
        post.setHasAttachments(value);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(post.getId())), Update.update("hasAttachments", value), Post.class);
        if (post.getTopicId() != null) {
            boolean topicHas = value || mongo.exists(Query.query(Criteria.where("topicId").is(post.getTopicId())
                .and("hasAttachments").is(true)), Post.class);
            mongo.updateFirst(Query.query(Criteria.where("_id").is(post.getTopicId())), Update.update("hasAttach", topicHas), Topic.class);
        }
    }

    /** Legacy extensionsForSecurity: extension -> allowed (group allow AND extension allow). */
    Map<String, Boolean> extensionRules() {
        Map<Integer, Boolean> groupAllow = new HashMap<>();
        for (AttachmentExtensionGroup g : extensionGroups.findAll()) {
            groupAllow.put(g.getId(), g.isAllow());
        }
        Map<String, Boolean> rules = new HashMap<>();
        for (AttachmentExtension e : extensions.findAll()) {
            if (e.getExtension() == null) {
                continue;
            }
            boolean allow = groupAllow.getOrDefault(e.getExtensionGroupId(), true) && e.isAllow();
            rules.put(e.getExtension().toLowerCase(Locale.ROOT), allow);
        }
        return rules;
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return s == null ? fallback : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
