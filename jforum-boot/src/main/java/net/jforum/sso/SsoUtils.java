package net.jforum.sso;

import java.util.Optional;

import net.jforum.domain.User;
import net.jforum.repository.UserRepository;
import net.jforum.user.UserService;

import org.springframework.stereotype.Component;

/** Legacy SSOUtils: looks the SSO user up and registers it on first access. */
@Component
public class SsoUtils {

    private final UserRepository users;
    private final UserService userService;

    public SsoUtils(UserRepository users, UserService userService) {
        this.users = users;
        this.userService = userService;
    }

    public Optional<User> find(String username) {
        return users.findByUsernameIgnoreCase(username);
    }

    public User register(String username, String password, String email) {
        return find(username).orElseGet(() -> userService.create(username, password, email, false));
    }
}
