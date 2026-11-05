package net.jforum.moderation;

import java.time.Instant;
import java.util.List;

import net.jforum.AbstractIntegrationTest;
import net.jforum.domain.Forum;
import net.jforum.domain.Group;
import net.jforum.domain.Post;
import net.jforum.domain.Role;
import net.jforum.domain.Topic;
import net.jforum.domain.User;
import net.jforum.security.ForumUserDetails;
import net.jforum.security.PermissionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/** Test data helpers shared by the moderation / search / RSS / attachment integration tests. */
public abstract class Track4Fixtures extends AbstractIntegrationTest {

    public static final int GENERAL = 1;
    public static final int ADMIN_GROUP = 2;

    @Autowired
    protected PermissionService permissionService;

    protected Forum forum(int id, String name) {
        Forum f = new Forum();
        f.setId(id);
        f.setCategoryId(1);
        f.setName(name);
        f.setOrder(id);
        return mongo.save(f);
    }

    /** Adds a value (forum id) to a role of a group, creating the role when missing. */
    protected void grant(int groupId, String roleName, String... values) {
        Group g = mongo.findById(groupId, Group.class);
        Role role = g.getRoles().stream().filter(r -> r.getName().equals(roleName)).findFirst().orElseGet(() -> {
            Role r = new Role();
            r.setName(roleName);
            g.getRoles().add(r);
            return r;
        });
        role.getValues().addAll(List.of(values));
        mongo.save(g);
        permissionService.invalidate();
    }

    protected Topic topic(int id, int forumId, String title, int userId, Instant time) {
        Topic t = new Topic();
        t.setId(id);
        t.setForumId(forumId);
        t.setTitle(title);
        t.setUserId(userId);
        t.setTime(time);
        t.setLastPostTime(time);
        return mongo.save(t);
    }

    protected Post addPost(int id, Topic topic, int userId, String username, String subject, String text, boolean moderate, Instant time) {
        Post p = new Post();
        p.setId(id);
        p.setTopicId(topic.getId());
        p.setForumId(topic.getForumId());
        p.setUserId(userId);
        p.setUsername(username);
        p.setSubject(subject);
        p.setText(text);
        p.setModerate(moderate);
        p.setTime(time);
        mongo.save(p);
        if (topic.getFirstPostId() == null) {
            topic.setFirstPostId(id);
        }
        if (!moderate) {
            topic.setLastPostId(id);
            topic.setLastPostTime(time);
        }
        mongo.save(topic);
        return p;
    }

    protected User member(int id, String username, int... groups) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setRegistrationDate(Instant.now());
        for (int g : groups) {
            u.getGroupIds().add(g);
        }
        return mongo.save(u);
    }

    protected static RequestPostProcessor as(int id, String username) {
        return user(new ForumUserDetails(id, username, "x", true));
    }

    protected static RequestPostProcessor admin() {
        return as(2, "Admin");
    }
}
