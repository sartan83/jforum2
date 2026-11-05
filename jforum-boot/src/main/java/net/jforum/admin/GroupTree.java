package net.jforum.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.jforum.domain.Group;
import net.jforum.repository.GroupRepository;

import org.springframework.stereotype.Component;

/** Groups flattened in parent/child order with their depth (legacy net.jforum.util.TreeGroup). */
@Component
public class GroupTree {

    private final GroupRepository groups;

    public GroupTree(GroupRepository groups) {
        this.groups = groups;
    }

    public static final class Node {

        private final Group group;
        private final int level;

        Node(Group group, int level) {
            this.group = group;
            this.level = level;
        }

        public Group getGroup() {
            return group;
        }

        public int getId() {
            return group.getId();
        }

        public String getName() {
            return group.getName();
        }

        public int getLevel() {
            return level;
        }
    }

    public List<Node> nodes() {
        List<Group> all = new ArrayList<>(groups.findAll());
        all.sort(Comparator.comparing(Group::getId));
        List<Node> result = new ArrayList<>();
        for (Group g : all) {
            if (g.getParentId() == null || g.getParentId() == 0 || all.stream().noneMatch(p -> p.getId().equals(g.getParentId()))) {
                add(g, 0, all, result);
            }
        }
        return result;
    }

    private void add(Group g, int level, List<Group> all, List<Node> result) {
        if (level > 20 || result.stream().anyMatch(n -> n.getId() == g.getId())) {
            return;
        }
        result.add(new Node(g, level));
        for (Group child : all) {
            if (Objects.equals(child.getParentId(), g.getId()) && !child.getId().equals(g.getId())) {
                add(child, level + 1, all, result);
            }
        }
    }
}
