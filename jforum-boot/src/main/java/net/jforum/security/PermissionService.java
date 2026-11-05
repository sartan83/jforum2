package net.jforum.security;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.jforum.domain.Group;
import net.jforum.domain.Role;
import net.jforum.domain.User;
import net.jforum.repository.GroupRepository;
import net.jforum.repository.UserRepository;

import org.springframework.stereotype.Service;

/** Loads and caches {@link PermissionControl} per user (legacy SecurityRepository). */
@Service
public class PermissionService {

    private final GroupRepository groups;
    private final UserRepository users;
    private final Map<Integer, PermissionControl> cache = new ConcurrentHashMap<>();

    public PermissionService(GroupRepository groups, UserRepository users) {
        this.groups = groups;
        this.users = users;
    }

    public PermissionControl forUser(User user) {
        return cache.computeIfAbsent(user.getId(), id -> load(user.getGroupIds()));
    }

    public PermissionControl forUserId(int userId) {
        PermissionControl pc = cache.get(userId);
        if (pc != null) {
            return pc;
        }
        return users.findById(userId).map(this::forUser).orElseGet(() -> new PermissionControl(Map.of()));
    }

    public PermissionControl forGroups(List<Integer> groupIds) {
        return load(groupIds);
    }

    private PermissionControl load(List<Integer> groupIds) {
        return PermissionControl.of(groups.findAllById(groupIds));
    }

    /** Call after any change to groups, roles or group membership. */
    public void invalidate() {
        cache.clear();
    }

    public void invalidate(int userId) {
        cache.remove(userId);
    }

    /**
     * Grants role/value to every group in {@code groupIds} (creating the role when missing). Used when a new
     * forum or category is created, mirroring legacy admin ForumAction/CategoryAction.addRole.
     */
    public void grant(String roleName, String value, Iterable<Integer> groupIds) {
        for (Group g : groups.findAllById(groupIds)) {
            Role role = g.getRoles().stream().filter(r -> r.getName().equals(roleName)).findFirst().orElseGet(() -> {
                Role r = new Role();
                r.setName(roleName);
                g.getRoles().add(r);
                return r;
            });
            if (value != null && !role.getValues().contains(value)) {
                role.getValues().add(value);
            }
            groups.save(g);
        }
        invalidate();
    }

    /** Removes {@code value} from {@code roleName} in every group (forum/category deletion). */
    public void revokeValue(String roleName, String value) {
        for (Group g : groups.findAll()) {
            boolean changed = false;
            for (Role r : g.getRoles()) {
                if (r.getName().equals(roleName)) {
                    changed |= r.getValues().remove(value);
                }
            }
            if (changed) {
                groups.save(g);
            }
        }
        invalidate();
    }
}
