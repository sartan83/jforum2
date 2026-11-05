package net.jforum.admin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.jforum.config.ForumConfig;
import net.jforum.format.SmilieService;
import net.jforum.repository.SmilieRepository;
import net.jforum.repository.UserSessionRepository;
import net.jforum.security.PermissionService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Legacy CacheAction (module adminCache): reload/clear the in-memory caches. */
@Controller
public class CacheAdminController {

    private final PermissionService permissions;
    private final SmilieService smilieService;
    private final SmilieRepository smilies;
    private final ForumConfig config;
    private final UserSessionRepository sessions;

    public CacheAdminController(PermissionService permissions, SmilieService smilieService, SmilieRepository smilies,
                                ForumConfig config, UserSessionRepository sessions) {
        this.permissions = permissions;
        this.smilieService = smilieService;
        this.smilies = smilies;
        this.config = config;
        this.sessions = sessions;
    }

    @GetMapping("/adminCache/list.page")
    public String list(@RequestParam(value = "done", required = false) String done, Model model) {
        model.addAttribute("smiliesCount", smilies.count());
        model.addAttribute("configCount", config.all().size());
        model.addAttribute("sessionsCount", sessions.count());
        model.addAttribute("done", done);
        return "admin/cache_list";
    }

    @PostMapping({"/adminCache/clear.page", "/adminCache/clearAll.page"})
    public String clear() {
        permissions.invalidate();
        smilieService.invalidate();
        config.reload();
        return "redirect:/adminCache/list.page?done=all";
    }

    @PostMapping("/adminCache/permissionsClear.page")
    public String permissionsClear() {
        permissions.invalidate();
        return "redirect:/adminCache/list.page?done=permissions";
    }

    @PostMapping("/adminCache/smiliesClear.page")
    public String smiliesClear() {
        smilieService.invalidate();
        return "redirect:/adminCache/list.page?done=smilies";
    }

    @PostMapping("/adminCache/configReload.page")
    public String configReload() {
        config.reload();
        return "redirect:/adminCache/list.page?done=config";
    }

    @PostMapping("/adminCache/sessionsClear.page")
    public String sessionsClear() {
        sessions.deleteByLastAccessBefore(Instant.now().minus(60, ChronoUnit.MINUTES));
        return "redirect:/adminCache/list.page?done=sessions";
    }
}
