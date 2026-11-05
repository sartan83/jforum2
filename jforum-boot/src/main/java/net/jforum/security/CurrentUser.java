package net.jforum.security;

import net.jforum.domain.User;
import net.jforum.repository.UserRepository;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Request-scoped view of the logged user (or the legacy Anonymous user, id 1) and their permissions.
 * Replacement for legacy SessionFacade.getUserSession() + SecurityRepository.
 */
@Component
@RequestScope
public class CurrentUser {

    private final UserRepository users;
    private final PermissionService permissions;
    private User user;
    private PermissionControl pc;

    public CurrentUser(UserRepository users, PermissionService permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    public User get() {
        if (user == null) {
            Integer id = userIdFromSecurityContext();
            user = id == null ? null : users.findById(id).orElse(null);
            if (user == null) {
                user = users.findById(AnonymousUser.ID).orElseGet(CurrentUser::transientAnonymous);
            }
        }
        return user;
    }

    public int getId() {
        return get().getId();
    }

    public boolean isLogged() {
        return get().getId() != AnonymousUser.ID;
    }

    public PermissionControl permissions() {
        if (pc == null) {
            pc = permissions.forUser(get());
        }
        return pc;
    }

    public boolean canAccess(String role) {
        return permissions().canAccess(role);
    }

    public boolean canAccess(String role, int value) {
        return permissions().canAccess(role, value);
    }

    public boolean isAdministrator() {
        return isLogged() && permissions().isAdministrator();
    }

    /** Forget the cached user, e.g. after the profile was updated in this request. */
    public void refresh() {
        user = null;
        pc = null;
    }

    private static Integer userIdFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ForumUserDetails d) {
            return d.getUserId();
        }
        return null;
    }

    private static User transientAnonymous() {
        User u = new User();
        u.setId(AnonymousUser.ID);
        u.setUsername("Anonymous");
        u.getGroupIds().add(AnonymousUser.GENERAL_GROUP_ID);
        return u;
    }
}
