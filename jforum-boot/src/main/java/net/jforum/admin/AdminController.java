package net.jforum.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.jforum.domain.User;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.UserRepository;
import net.jforum.repository.UserSessionRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Admin control panel home (legacy AdminAction: login/menu/main). Access is enforced by SecurityConfig on /adm*. */
@Controller
public class AdminController {

    private final PostRepository posts;
    private final TopicRepository topics;
    private final UserRepository users;
    private final UserSessionRepository sessions;

    public AdminController(PostRepository posts, TopicRepository topics, UserRepository users, UserSessionRepository sessions) {
        this.posts = posts;
        this.topics = topics;
        this.users = users;
        this.sessions = sessions;
    }

    @GetMapping({"/admBase/login.page", "/admBase/list.page", "/admBase/main.page"})
    public String login(Model model) {
        long totalPosts = posts.count();
        long totalTopics = topics.count();
        long totalUsers = Math.max(0, users.countByDeletedFalse() - 1);
        Instant start = users.findAll().stream().map(User::getRegistrationDate).filter(d -> d != null)
            .min(Comparator.naturalOrder()).orElse(Instant.now());
        double days = Math.max(1, Duration.between(start, Instant.now()).toDays());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("posts", totalPosts);
        stats.put("topics", totalTopics);
        stats.put("users", totalUsers);
        stats.put("postsPerDay", String.format("%.2f", totalPosts / days));
        stats.put("topicsPerDay", String.format("%.2f", totalTopics / days));
        stats.put("usersPerDay", String.format("%.2f", totalUsers / days));
        model.addAttribute("stats", stats);
        model.addAttribute("sessions", sessions.findByLastAccessAfter(Instant.now().minus(60, ChronoUnit.MINUTES)));
        return "admin/index";
    }

    @GetMapping("/admBase/menu.page")
    public String menu() {
        return "admin/menu";
    }
}
