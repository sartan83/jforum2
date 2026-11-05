package net.jforum.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jforum.domain.Group;
import net.jforum.domain.Role;

/**
 * Effective permissions of a user: the union of the roles of all of their groups.
 * Same semantics as legacy PermissionControl:
 * <ul>
 * <li>{@code canAccess(role)}: the role is granted (single permissions such as perm_administration).</li>
 * <li>{@code canAccess(role, value)}: the role is granted and {@code value} (a forum/category id) is in its
 * whitelist. E.g. perm_forum/5 = may see forum 5, perm_read_only_forums/5 = may WRITE in forum 5,
 * perm_reply_only/5 = may create topics in forum 5, perm_html_disabled/5 = HTML allowed in forum 5.</li>
 * </ul>
 */
public final class PermissionControl {

    private final Map<String, Set<String>> roles;

    public PermissionControl(Map<String, Set<String>> roles) {
        this.roles = roles;
    }

    public static PermissionControl of(List<Group> groups) {
        Map<String, Set<String>> roles = new HashMap<>();
        for (Group g : groups) {
            for (Role r : g.getRoles()) {
                roles.computeIfAbsent(r.getName(), k -> new HashSet<>()).addAll(r.getValues());
            }
        }
        return new PermissionControl(roles);
    }

    public boolean canAccess(String roleName) {
        return roles.containsKey(roleName);
    }

    public boolean canAccess(String roleName, String value) {
        Set<String> values = roles.get(roleName);
        return values != null && values.contains(value);
    }

    public boolean canAccess(String roleName, int id) {
        return canAccess(roleName, Integer.toString(id));
    }

    public boolean isAdministrator() {
        return canAccess(SecurityConstants.PERM_ADMINISTRATION);
    }

    public boolean isModerator(int forumId) {
        return canAccess(SecurityConstants.PERM_MODERATION) && canAccess(SecurityConstants.PERM_MODERATION_FORUMS, forumId);
    }

    public Set<String> values(String roleName) {
        return Collections.unmodifiableSet(roles.getOrDefault(roleName, Set.of()));
    }
}
