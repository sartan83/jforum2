package net.jforum.bootstrap;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import net.jforum.domain.Category;
import net.jforum.domain.Forum;
import net.jforum.domain.Group;
import net.jforum.domain.LastPostInfo;
import net.jforum.domain.Post;
import net.jforum.domain.Ranking;
import net.jforum.domain.Role;
import net.jforum.domain.Smilie;
import net.jforum.domain.Topic;
import net.jforum.domain.User;
import net.jforum.repository.CategoryRepository;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.GroupRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.RankingRepository;
import net.jforum.repository.SmilieRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.AnonymousUser;
import net.jforum.security.SecurityConstants;
import net.jforum.support.SequenceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Initial data equivalent to the legacy installer + database/*_data_dump.sql: groups General(1) and
 * Administration(2) with their roles, Anonymous(1) and Admin(2) users, smilies, ranks, and the
 * "Category Test / Test Forum / Welcome to JForum" content. Runs only on an empty database.
 */
@Component
@Order(10)
public class BootstrapDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapDataLoader.class);

    static final String[][] SMILIES = {
        new String[] {":)", "3b63d1616c5dfcf29f8a7a031aaa7cad.gif"},
        new String[] {":-)", "3b63d1616c5dfcf29f8a7a031aaa7cad.gif"},
        new String[] {":D", "283a16da79f3aa23fe1025c96295f04f.gif"},
        new String[] {":-D", "283a16da79f3aa23fe1025c96295f04f.gif"},
        new String[] {":(", "9d71f0541cff0a302a0309c5079e8dee.gif"},
        new String[] {":mrgreen:", "ed515dbff23a0ee3241dcc0a601c9ed6.gif"},
        new String[] {":-o", "47941865eb7bbc2a777305b46cc059a2.gif"},
        new String[] {":shock:", "385970365b8ed7503b4294502a458efa.gif"},
        new String[] {":?:", "0a4d7238daa496a758252d0a2b1a1384.gif"},
        new String[] {"8)", "b2eb59423fbf5fa39342041237025880.gif"},
        new String[] {":lol:", "97ada74b88049a6d50a6ed40898a03d7.gif"},
        new String[] {":x", "1069449046bcd664c21db15b1dfedaee.gif"},
        new String[] {":P", "69934afc394145350659cd7add244ca9.gif"},
        new String[] {":-P", "69934afc394145350659cd7add244ca9.gif"},
        new String[] {":oops:", "499fd50bc713bfcdf2ab5a23c00c2d62.gif"},
        new String[] {":cry:", "c30b4198e0907b23b8246bdd52aa1c3c.gif"},
        new String[] {":evil:", "2e207fad049d4d292f60607f80f05768.gif"},
        new String[] {":twisted:", "908627bbe5e9f6a080977db8c365caff.gif"},
        new String[] {":roll:", "2786c5c8e1a8be796fb2f726cca5a0fe.gif"},
        new String[] {":wink:", "8a80c6485cd926be453217d59a84a888.gif"},
        new String[] {";)", "8a80c6485cd926be453217d59a84a888.gif"},
        new String[] {";-)", "8a80c6485cd926be453217d59a84a888.gif"},
        new String[] {":!:", "9293feeb0183c67ea1ea8c52f0dbaf8c.gif"},
        new String[] {":?", "136dd33cba83140c7ce38db096d05aed.gif"},
        new String[] {":idea:", "8f7fb9dd46fb8ef86f81154a4feaada9.gif"},
        new String[] {":arrow:", "d6741711aa045b812616853b5507fd2a.gif"},
        new String[] {":hunf:", "0320a00cb4bb5629ab9fc2bc1fcc4e9e.gif"},
        new String[] {":-(", "9d71f0541cff0a302a0309c5079e8dee.gif"},
        new String[] {":XD:", "49869fe8223507d7223db3451e5321aa.gif"},
        new String[] {":thumbup:", "e8a506dc4ad763aca51bec4ca7dc8560.gif"},
        new String[] {":thumbdown:", "e78feac27fa924c4d0ad6cf5819f3554.gif"},
        new String[] {":|", "1cfd6e2a9a2c0cf8e74b49b35e2e46c7.gif"}
    };

    private final GroupRepository groups;
    private final UserRepository users;
    private final SmilieRepository smilies;
    private final RankingRepository rankings;
    private final CategoryRepository categories;
    private final ForumRepository forums;
    private final TopicRepository topics;
    private final PostRepository posts;
    private final SequenceService sequences;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;
    private final boolean enabled;

    public BootstrapDataLoader(GroupRepository groups, UserRepository users, SmilieRepository smilies, RankingRepository rankings,
                               CategoryRepository categories, ForumRepository forums, TopicRepository topics, PostRepository posts,
                               SequenceService sequences, PasswordEncoder passwordEncoder,
                               @Value("${jforum.bootstrap.admin-password:admin}") String adminPassword,
                               @Value("${jforum.bootstrap.enabled:true}") boolean enabled) {
        this.groups = groups;
        this.users = users;
        this.smilies = smilies;
        this.rankings = rankings;
        this.categories = categories;
        this.forums = forums;
        this.topics = topics;
        this.posts = posts;
        this.sequences = sequences;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || groups.count() > 0 || users.count() > 0) {
            return;
        }
        log.info("Empty database: loading JForum bootstrap data (admin user 'Admin')");
        Group general = group(AnonymousUser.GENERAL_GROUP_ID, "General", "General Users",
            SecurityConstants.PERM_VOTE, SecurityConstants.PERM_KARMA_ENABLED, SecurityConstants.PERM_CREATE_POLL,
            SecurityConstants.PERM_BOOKMARKS_ENABLED, SecurityConstants.PERM_ATTACHMENTS_DOWNLOAD);
        Group admin = group(AnonymousUser.ADMIN_GROUP_ID, "Administration", "Admin Users",
            SecurityConstants.PERM_ADMINISTRATION, SecurityConstants.PERM_MODERATION, SecurityConstants.PERM_MODERATION_POST_REMOVE,
            SecurityConstants.PERM_MODERATION_POST_EDIT, SecurityConstants.PERM_MODERATION_TOPIC_MOVE,
            SecurityConstants.PERM_MODERATION_TOPIC_LOCK_UNLOCK, SecurityConstants.PERM_MODERATION_APPROVE_MESSAGES,
            SecurityConstants.PERM_CREATE_STICKY_ANNOUNCEMENT_TOPICS, SecurityConstants.PERM_VOTE, SecurityConstants.PERM_CREATE_POLL,
            SecurityConstants.PERM_KARMA_ENABLED, SecurityConstants.PERM_BOOKMARKS_ENABLED, SecurityConstants.PERM_ATTACHMENTS_DOWNLOAD,
            SecurityConstants.PERM_MODERATION_LOG, SecurityConstants.PERM_FULL_MODERATION_LOG);
        // Forum-scoped roles: values hold the forum/category ids they apply to (filled below for forum 1 / category 1)
        for (Group g : List.of(general, admin)) {
            for (String r : List.of(SecurityConstants.PERM_FORUM, SecurityConstants.PERM_ANONYMOUS_POST, SecurityConstants.PERM_CATEGORY,
                SecurityConstants.PERM_READ_ONLY_FORUMS, SecurityConstants.PERM_HTML_DISABLED, SecurityConstants.PERM_ATTACHMENTS_ENABLED,
                SecurityConstants.PERM_REPLY_ONLY, SecurityConstants.PERM_REPLY_WITHOUT_MODERATION)) {
                g.getRoles().add(role(r, "1"));
            }
        }
        admin.getRoles().add(role(SecurityConstants.PERM_MODERATION_FORUMS, "1"));
        groups.saveAll(List.of(general, admin));

        Instant now = Instant.now();
        User anonymous = user(AnonymousUser.ID, "Anonymous", "nopass", AnonymousUser.GENERAL_GROUP_ID, now);
        anonymous.setPassword(null);
        User adminUser = user(2, "Admin", passwordEncoder.encode(adminPassword), AnonymousUser.ADMIN_GROUP_ID, now);
        adminUser.setEmail("admin@localhost");
        adminUser.setTotalPosts(1);
        users.saveAll(List.of(anonymous, adminUser));

        int sid = 1;
        for (String[] s : SMILIES) {
            Smilie smilie = new Smilie();
            smilie.setId(sid++);
            smilie.setCode(s[0]);
            smilie.setDiskName(s[1]);
            smilie.setUrl("/images/smilies/" + s[1]);
            smilies.save(smilie);
        }

        Ranking rank = new Ranking();
        rank.setId(1);
        rank.setTitle("Stranger");
        rank.setMin(0);
        rankings.save(rank);

        Category category = new Category();
        category.setId(1);
        category.setTitle("Category Test");
        category.setOrder(1);
        categories.save(category);

        Instant topicTime = LocalDateTime.of(2005, 1, 4, 16, 59, 54).toInstant(ZoneOffset.UTC);
        Forum forum = new Forum();
        forum.setId(1);
        forum.setCategoryId(1);
        forum.setName("Test Forum");
        forum.setDescription("This is a test forum");
        forum.setOrder(1);
        forum.setTotalTopics(1);
        forum.setTotalPosts(1);
        forum.setLastPostId(1);
        LastPostInfo lpi = new LastPostInfo();
        lpi.setPostId(1);
        lpi.setTopicId(1);
        lpi.setUserId(2);
        lpi.setUsername("Admin");
        lpi.setPostTime(topicTime);
        forum.setLastPostInfo(lpi);
        forums.save(forum);

        Topic topic = new Topic();
        topic.setId(1);
        topic.setForumId(1);
        topic.setTitle("Welcome to JForum");
        topic.setUserId(2);
        topic.setUsername("Admin");
        topic.setTime(topicTime);
        topic.setFirstPostId(1);
        topic.setLastPostId(1);
        topic.setLastPostTime(topicTime);
        topic.setLastPostUserId(2);
        topic.setLastPostUsername("Admin");
        topics.save(topic);

        Post post = new Post();
        post.setId(1);
        post.setTopicId(1);
        post.setForumId(1);
        post.setUserId(2);
        post.setUsername("Admin");
        post.setTime(topicTime);
        post.setUserIp("127.0.0.1");
        post.setSubject("Welcome to JForum");
        post.setText("[b][color=blue][size=18]Congratulations :!: [/size][/color][/b]\nYou have completed the installation, "
            + "and JForum is up and running. \n\nTo start administering the board, login as [i]Admin / <your password>[/i] "
            + "and look for a link [b][url=/admBase/login.page]Admin Control Panel[/url][/b] somewhere in the page. \n\n"
            + "Have fun :)");
        posts.save(post);

        sequences.ensureAtLeast("groups", 2);
        sequences.ensureAtLeast("users", 2);
        sequences.ensureAtLeast("smilies", sid - 1);
        sequences.ensureAtLeast("rankings", 1);
        sequences.ensureAtLeast("categories", 1);
        sequences.ensureAtLeast("forums", 1);
        sequences.ensureAtLeast("topics", 1);
        sequences.ensureAtLeast("posts", 1);
    }

    private static Group group(int id, String name, String description, String... roles) {
        Group g = new Group();
        g.setId(id);
        g.setName(name);
        g.setDescription(description);
        for (String r : roles) {
            g.getRoles().add(role(r));
        }
        return g;
    }

    private static Role role(String name, String... values) {
        Role r = new Role();
        r.setName(name);
        r.getValues().addAll(List.of(values));
        return r;
    }

    private static User user(int id, String username, String password, int groupId, Instant now) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(password);
        u.setRegistrationDate(now);
        u.getGroupIds().add(groupId);
        return u;
    }
}
