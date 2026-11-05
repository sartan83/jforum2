package net.jforum.security;

import net.jforum.domain.User;
import net.jforum.repository.UserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users for form login. Legacy MD5 hashes (migrated from jforum_users.user_password) are accepted
 * and transparently re-hashed with bcrypt on the next successful login via {@link #updatePassword}.
 */
@Service
public class ForumUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UserRepository users;

    public ForumUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = users.findByUsernameIgnoreCase(username)
            .filter(u -> !u.isDeleted() && u.getId() != AnonymousUser.ID)
            .orElseThrow(() -> new UsernameNotFoundException(username));
        return toDetails(user);
    }

    @Override
    public UserDetails updatePassword(UserDetails details, String newPassword) {
        User user = users.findByUsernameIgnoreCase(details.getUsername()).orElseThrow();
        user.setPassword(newPassword);
        users.save(user);
        return toDetails(user);
    }

    private static ForumUserDetails toDetails(User user) {
        return new ForumUserDetails(user.getId(), user.getUsername(), user.getPassword(), user.isActive());
    }
}
