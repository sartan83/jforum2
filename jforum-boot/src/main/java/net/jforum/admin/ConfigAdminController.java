package net.jforum.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.config.ForumConfig;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** Legacy ConfigAction (module adminConfig): every "p_&lt;key&gt;" parameter is persisted as a Mongo override. */
@Controller
public class ConfigAdminController {

    /** Keys shown by legacy config_list.htm, in the same order. */
    static final List<String> KEYS = List.of(
        "forum.name", "forum.page.title", "forum.link", "homepage.link", "forum.page.metatag.keywords",
        "forum.page.metatag.description", "i18n.board.default", "encoding", "topicsPerPage", "postsPerPage",
        "usersPerPage", "hot.topic.begin", "topic.recent", "html.tags.welcome", "avatar.maxWidth", "avatar.maxHeight",
        "avatarMaxKbSize", "registration.enabled", "captcha.registration", "rss.enabled", "posts.new.delay",
        "posts.cache.enabled", "posts.cache.size", "mail.sender", "mail.charset", "mail.messageFormat",
        "mail.smtp.host", "mail.smtp.port", "mail.smtp.auth", "mail.smtp.ssl", "mail.smtp.username",
        "mail.smtp.password", "mail.user.email.auth", "mail.notify.answers", "mail.newAnswer.subject",
        "mail.newAnswer.messageFile", "mail.newPm.subject", "mail.newPm.messageFile", "mail.activationKey.subject",
        "mail.activationKey.messageFile", "mail.lostPassword.subject", "mail.lostPassword.messageFile");

    private final ForumConfig config;

    public ConfigAdminController(ForumConfig config) {
        this.config = config;
    }

    public static final class Entry {

        private final String key;
        private final String value;
        private final boolean bool;

        Entry(String key, String value) {
            this.key = key;
            this.value = value == null ? "" : value;
            this.bool = "true".equalsIgnoreCase(this.value) || "false".equalsIgnoreCase(this.value);
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public boolean isBool() {
            return bool;
        }
    }

    @GetMapping("/adminConfig/list.page")
    public String list(Model model) {
        List<Entry> entries = new ArrayList<>();
        KEYS.forEach(k -> entries.add(new Entry(k, config.get(k, ""))));
        Map<String, String> others = new TreeMap<>(config.all());
        KEYS.forEach(others::remove);
        List<Entry> advanced = new ArrayList<>();
        others.forEach((k, v) -> advanced.add(new Entry(k, v)));
        model.addAttribute("entries", entries);
        model.addAttribute("advanced", advanced);
        return "admin/config_list";
    }

    @PostMapping({"/adminConfig/editSave.page", "/adminConfig/edit.page"})
    public String editSave(HttpServletRequest request) {
        request.getParameterMap().forEach((name, values) -> {
            if (name.startsWith("p_") && values.length > 0) {
                String key = name.substring(2);
                String value = values[0] == null ? "" : values[0].trim();
                if (!value.equals(config.get(key, ""))) {
                    config.set(key, value);
                }
            }
        });
        return "redirect:/adminConfig/list.page";
    }
}
