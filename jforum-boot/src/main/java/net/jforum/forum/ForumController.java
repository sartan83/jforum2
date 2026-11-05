package net.jforum.forum;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.jforum.repository.PostRepository;
import net.jforum.repository.UserRepository;
import net.jforum.repository.UserSessionRepository;
import net.jforum.security.CurrentUser;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Forum index (legacy module "forums", action "list"). */
@Controller
public class ForumController {

    private final ForumService forumService;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final PostRepository posts;
    private final UserSessionRepository sessions;

    public ForumController(ForumService forumService, CurrentUser currentUser, UserRepository users, PostRepository posts,
                           UserSessionRepository sessions) {
        this.forumService = forumService;
        this.currentUser = currentUser;
        this.users = users;
        this.posts = posts;
        this.sessions = sessions;
    }

    @GetMapping({"/forums/list.page", "/forums/list"})
    public String list(Model model) {
        model.addAttribute("categories", forumService.visibleCategories(currentUser.permissions()));
        model.addAttribute("totalRegisteredUsers", Math.max(0, users.countByDeletedFalse() - 1));
        model.addAttribute("totalMessages", posts.count());
        users.findTopByDeletedFalseOrderByIdDesc().ifPresent(u -> model.addAttribute("lastUser", u));
        var online = sessions.findByLastAccessAfter(Instant.now().minus(15, ChronoUnit.MINUTES));
        long registered = online.stream().filter(s -> s.isLogged()).count();
        model.addAttribute("onlineRegistered", registered);
        model.addAttribute("onlineGuests", online.size() - registered);
        model.addAttribute("onlineUsers", online.stream().filter(s -> s.isLogged()).map(s -> s.getUsername()).distinct().toList());
        return "forum_list";
    }
}
