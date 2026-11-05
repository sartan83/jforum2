package net.jforum.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/** Spring Security principal carrying the numeric JForum user id. */
public class ForumUserDetails extends User {

    private final int userId;

    public ForumUserDetails(int userId, String username, String password, boolean enabled) {
        super(username, password == null ? "" : password, enabled, true, true, true,
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.userId = userId;
    }

    public int getUserId() {
        return userId;
    }
}
