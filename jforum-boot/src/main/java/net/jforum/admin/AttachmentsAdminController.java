package net.jforum.admin;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.config.ForumConfig;
import net.jforum.domain.AttachmentExtension;
import net.jforum.domain.AttachmentExtensionGroup;
import net.jforum.domain.QuotaLimit;
import net.jforum.repository.AttachmentExtensionGroupRepository;
import net.jforum.repository.AttachmentExtensionRepository;
import net.jforum.repository.QuotaLimitRepository;
import net.jforum.support.Ids;
import net.jforum.support.SequenceService;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Legacy AttachmentsAction (module adminAttachments): configuration, quota limits, extension groups, extensions. */
@Controller
public class AttachmentsAdminController {

    static final List<String> CONFIG_KEYS = List.of("attachments.max.post", "attachments.images.createthumb",
        "attachments.images.thumb.maxsize.w", "attachments.images.thumb.maxsize.h", "attachments.images.thumb.box.show",
        "attachments.icon");

    private final ForumConfig config;
    private final QuotaLimitRepository quotas;
    private final AttachmentExtensionGroupRepository extensionGroups;
    private final AttachmentExtensionRepository extensions;
    private final GroupTree groupTree;
    private final SequenceService sequences;

    public AttachmentsAdminController(ForumConfig config, QuotaLimitRepository quotas,
                                      AttachmentExtensionGroupRepository extensionGroups,
                                      AttachmentExtensionRepository extensions, GroupTree groupTree,
                                      SequenceService sequences) {
        this.config = config;
        this.quotas = quotas;
        this.extensionGroups = extensionGroups;
        this.extensions = extensions;
        this.groupTree = groupTree;
        this.sequences = sequences;
    }

    @GetMapping("/adminAttachments/configurations.page")
    public String configurations(Model model) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        CONFIG_KEYS.forEach(k -> values.put(k, config.get(k, "")));
        model.addAttribute("values", values);
        return "admin/attachments_config";
    }

    @PostMapping("/adminAttachments/configurationsSave.page")
    public String configurationsSave(HttpServletRequest request) {
        for (String key : CONFIG_KEYS) {
            String value = request.getParameter("p_" + key);
            if (value != null && !value.trim().equals(config.get(key, ""))) {
                config.set(key, value.trim());
            }
        }
        return "redirect:/adminAttachments/configurations.page";
    }

    // Quota limits

    @GetMapping("/adminAttachments/quotaLimit.page")
    public String quotaLimit(Model model) {
        List<QuotaLimit> all = quotas.findAll(Sort.by("id"));
        model.addAttribute("quotas", all);
        model.addAttribute("groups", groupTree.nodes());
        java.util.Map<String, Integer> groupQuota = new java.util.HashMap<>();
        all.forEach(q -> q.getGroupIds().forEach(g -> groupQuota.put(Integer.toString(g), q.getId())));
        model.addAttribute("groupQuota", groupQuota);
        return "admin/quota_limit";
    }

    @PostMapping("/adminAttachments/quotaLimitSave.page")
    public String quotaLimitSave(@RequestParam("quota_description") String description,
                                 @RequestParam("max_filesize") String size,
                                 @RequestParam(value = "type", required = false) String type) {
        QuotaLimit q = new QuotaLimit();
        q.setId(sequences.next("quotaLimits"));
        q.setDescription(AdminSupport.trim(description));
        q.setSize(AdminSupport.parseInt(size, 0));
        q.setType(AdminSupport.parseInt(type, 1));
        quotas.save(q);
        return "redirect:/adminAttachments/quotaLimit.page";
    }

    @PostMapping("/adminAttachments/quotaLimitUpdate.page")
    public String quotaLimitUpdate(HttpServletRequest request) {
        int total = AdminSupport.parseInt(request.getParameter("total_records"), 0);
        for (int i = 0; i < total; i++) {
            Integer id = Ids.parse(request.getParameter("id_" + i));
            if (id == null) {
                continue;
            }
            quotas.findById(id).ifPresent(q -> {
                q.setDescription(AdminSupport.trim(request.getParameter("quota_desc_" + q.getId())));
                q.setSize(AdminSupport.parseInt(request.getParameter("max_filesize_" + q.getId()), q.getSize()));
                q.setType(AdminSupport.parseInt(request.getParameter("type_" + q.getId()), q.getType()));
                quotas.save(q);
            });
        }
        for (Integer id : AdminSupport.ids(request.getParameterValues("delete"))) {
            quotas.deleteById(id);
        }
        return "redirect:/adminAttachments/quotaLimit.page";
    }

    @PostMapping("/adminAttachments/quotaGroupsSave.page")
    public String quotaGroupsSave(HttpServletRequest request) {
        List<QuotaLimit> all = quotas.findAll();
        all.forEach(q -> q.setGroupIds(new ArrayList<>()));
        for (GroupTree.Node g : groupTree.nodes()) {
            Integer quotaId = Ids.parse(request.getParameter("limit_" + g.getId()));
            if (quotaId != null) {
                all.stream().filter(q -> q.getId().equals(quotaId)).findFirst().ifPresent(q -> q.getGroupIds().add(g.getId()));
            }
        }
        quotas.saveAll(all);
        return "redirect:/adminAttachments/quotaLimit.page";
    }

    // Extension groups

    @GetMapping("/adminAttachments/extensionGroups.page")
    public String extensionGroups(Model model) {
        model.addAttribute("groups", extensionGroups.findAll(Sort.by("id")));
        return "admin/extension_groups";
    }

    @PostMapping("/adminAttachments/extensionGroupsSave.page")
    public String extensionGroupsSave(@RequestParam("name") String name,
                                      @RequestParam(value = "allow", required = false) String allow,
                                      @RequestParam(value = "upload_icon", required = false) String icon,
                                      @RequestParam(value = "download_mode", required = false) String downloadMode) {
        AttachmentExtensionGroup g = new AttachmentExtensionGroup();
        g.setId(sequences.next("extensionGroups"));
        g.setName(AdminSupport.trim(name));
        g.setAllow("1".equals(allow));
        g.setUploadIcon(AdminSupport.trim(icon));
        g.setDownloadMode(AdminSupport.parseInt(downloadMode, 1));
        extensionGroups.save(g);
        return "redirect:/adminAttachments/extensionGroups.page";
    }

    @PostMapping("/adminAttachments/extensionGroupsUpdate.page")
    public String extensionGroupsUpdate(HttpServletRequest request) {
        int total = AdminSupport.parseInt(request.getParameter("total_records"), 0);
        for (int i = 0; i < total; i++) {
            Integer id = Ids.parse(request.getParameter("id_" + i));
            if (id == null) {
                continue;
            }
            final int n = i;
            extensionGroups.findById(id).ifPresent(g -> {
                g.setName(AdminSupport.trim(request.getParameter("name_" + n)));
                g.setAllow("1".equals(request.getParameter("allow_" + n)));
                g.setUploadIcon(AdminSupport.trim(request.getParameter("upload_icon_" + n)));
                g.setDownloadMode(AdminSupport.parseInt(request.getParameter("download_mode_" + n), g.getDownloadMode()));
                extensionGroups.save(g);
            });
        }
        for (Integer id : AdminSupport.ids(request.getParameterValues("delete"))) {
            extensionGroups.deleteById(id);
            extensions.findAll().stream().filter(e -> id.equals(e.getExtensionGroupId())).forEach(extensions::delete);
        }
        return "redirect:/adminAttachments/extensionGroups.page";
    }

    // Extensions

    @GetMapping("/adminAttachments/extensions.page")
    public String extensions(Model model) {
        model.addAttribute("extensions", extensions.findAll(Sort.by("id")));
        model.addAttribute("groups", extensionGroups.findAll(Sort.by("id")));
        return "admin/extensions";
    }

    @PostMapping("/adminAttachments/extensionsSave.page")
    public String extensionsSave(@RequestParam("extension") String extension,
                                 @RequestParam(value = "comment", required = false) String comment,
                                 @RequestParam(value = "upload_icon", required = false) String icon,
                                 @RequestParam(value = "extension_group", required = false) String group,
                                 @RequestParam(value = "allow", required = false) String allow) {
        String ext = AdminSupport.trim(extension).toLowerCase(java.util.Locale.ROOT);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        if (!ext.isEmpty() && extensions.findByExtensionIgnoreCase(ext).isEmpty()) {
            AttachmentExtension e = new AttachmentExtension();
            e.setId(sequences.next("extensions"));
            e.setExtension(ext);
            e.setDescription(AdminSupport.trim(comment));
            e.setUploadIcon(AdminSupport.trim(icon));
            e.setExtensionGroupId(Ids.parse(group));
            e.setAllow("1".equals(allow));
            extensions.save(e);
        }
        return "redirect:/adminAttachments/extensions.page";
    }

    @PostMapping("/adminAttachments/extensionsUpdate.page")
    public String extensionsUpdate(HttpServletRequest request) {
        int total = AdminSupport.parseInt(request.getParameter("total_records"), 0);
        for (int i = 0; i < total; i++) {
            Integer id = Ids.parse(request.getParameter("id_" + i));
            if (id == null) {
                continue;
            }
            final int n = i;
            extensions.findById(id).ifPresent(e -> {
                e.setDescription(AdminSupport.trim(request.getParameter("comment_" + n)));
                e.setUploadIcon(AdminSupport.trim(request.getParameter("upload_icon_" + n)));
                e.setExtensionGroupId(Ids.parse(request.getParameter("extension_group_" + n)));
                e.setAllow("1".equals(request.getParameter("allow_" + n)));
                extensions.save(e);
            });
        }
        for (Integer id : AdminSupport.ids(request.getParameterValues("delete"))) {
            extensions.deleteById(id);
        }
        return "redirect:/adminAttachments/extensions.page";
    }
}
