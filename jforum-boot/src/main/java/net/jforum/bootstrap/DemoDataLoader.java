package net.jforum.bootstrap;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import net.jforum.domain.Category;
import net.jforum.domain.Forum;
import net.jforum.domain.Group;
import net.jforum.domain.LastPostInfo;
import net.jforum.domain.Post;
import net.jforum.domain.Role;
import net.jforum.domain.Topic;
import net.jforum.domain.User;
import net.jforum.repository.CategoryRepository;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.GroupRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.AnonymousUser;
import net.jforum.security.SecurityConstants;
import net.jforum.support.SequenceService;

/**
 * Optional demo content (JFORUM_SEED_DEMO=true): extra categories, forums, users, topics and posts on top of the
 * legacy bootstrap data. Runs once: skipped when the demo users already exist.
 */
@Component
@Order(20)
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    static final String DEMO_PASSWORD = "demo123";
    static final String[] DEMO_USERS = {"mario", "giulia", "luca"};

    private final boolean enabled;
    private final CategoryRepository categories;
    private final ForumRepository forums;
    private final TopicRepository topics;
    private final PostRepository posts;
    private final UserRepository users;
    private final GroupRepository groups;
    private final SequenceService sequences;
    private final PasswordEncoder passwordEncoder;

    public DemoDataLoader(@Value("${jforum.seed.demo:false}") boolean enabled, CategoryRepository categories,
                          ForumRepository forums, TopicRepository topics, PostRepository posts, UserRepository users,
                          GroupRepository groups, SequenceService sequences, PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.categories = categories;
        this.forums = forums;
        this.topics = topics;
        this.posts = posts;
        this.users = users;
        this.groups = groups;
        this.sequences = sequences;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            seed();
        }
    }

    public void seed() {
        if (users.findByUsernameIgnoreCase(DEMO_USERS[0]).isPresent()) {
            return;
        }
        log.info("Seeding demo data (JFORUM_SEED_DEMO=true)");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        List<User> demoUsers = new ArrayList<>();
        for (String name : DEMO_USERS) {
            User u = new User();
            u.setId(sequences.next("users"));
            u.setUsername(name);
            u.setEmail(name + "@example.com");
            u.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
            u.setRegistrationDate(now.minus(30, ChronoUnit.DAYS));
            u.getGroupIds().add(AnonymousUser.GENERAL_GROUP_ID);
            demoUsers.add(users.save(u));
        }

        String[][] structure = {
            {"Generale", "Annunci|Novità e comunicazioni dello staff", "Presentazioni|Presentati alla community"},
            {"Sviluppo", "Java & Spring Boot|Domande su Java 21 e Spring Boot 3", "MongoDB|Modellazione documenti, indici, query"},
        };
        String[][] topicTitles = {
            {"Benvenuti nel nuovo forum!", "Regole della community"},
            {"Ciao a tutti, sono Mario", "Nuovo utente da Milano"},
            {"Record vs classi in Java 21", "Virtual threads in Spring Boot 3.5"},
            {"Embedding o referencing per i post?", "Text index vs Atlas Search"},
        };

        int forumIndex = 0;
        int order = 2;
        for (String[] cat : structure) {
            Category c = new Category();
            c.setId(sequences.next("categories"));
            c.setTitle(cat[0]);
            c.setOrder(order++);
            categories.save(c);
            for (int f = 1; f < cat.length; f++) {
                String[] nd = cat[f].split("\\|");
                Forum forum = new Forum();
                forum.setId(sequences.next("forums"));
                forum.setCategoryId(c.getId());
                forum.setName(nd[0]);
                forum.setDescription(nd[1]);
                forum.setOrder(f);
                forums.save(forum);
                grantForum(c.getId(), forum.getId());
                for (String title : topicTitles[forumIndex]) {
                    createTopic(forum, title, demoUsers, now);
                }
                forumIndex++;
            }
        }
    }

    private void createTopic(Forum forum, String title, List<User> demoUsers, Instant now) {
        Topic topic = new Topic();
        topic.setId(sequences.next("topics"));
        topic.setForumId(forum.getId());
        topic.setTitle(title);
        Instant time = now.minus(5 + topic.getId() % 7, ChronoUnit.DAYS);
        topic.setTime(time);
        Post last = null;
        for (int i = 0; i < 3; i++) {
            User author = demoUsers.get((topic.getId() + i) % demoUsers.size());
            Post p = new Post();
            p.setId(sequences.next("posts"));
            p.setTopicId(topic.getId());
            p.setForumId(forum.getId());
            p.setUserId(author.getId());
            p.setUsername(author.getUsername());
            p.setTime(time.plus(i, ChronoUnit.HOURS));
            p.setUserIp("127.0.0.1");
            p.setSubject(i == 0 ? title : "Re: " + title);
            p.setText(i == 0
                ? "[b]" + title + "[/b]\nPrimo messaggio della discussione, con un po' di [i]BBCode[/i] :)"
                : "[quote=" + (last != null ? last.getUsername() : "") + "]Interessante![/quote]\nSono d'accordo, grazie per aver condiviso.");
            posts.save(p);
            if (i == 0) {
                topic.setUserId(author.getId());
                topic.setUsername(author.getUsername());
                topic.setFirstPostId(p.getId());
            }
            author.setTotalPosts(author.getTotalPosts() + 1);
            users.save(author);
            last = p;
        }
        topic.setReplies(2);
        topic.setLastPostId(last.getId());
        topic.setLastPostTime(last.getTime());
        topic.setLastPostUserId(last.getUserId());
        topic.setLastPostUsername(last.getUsername());
        topics.save(topic);

        forum.setTotalTopics(forum.getTotalTopics() + 1);
        forum.setTotalPosts(forum.getTotalPosts() + 3);
        forum.setLastPostId(last.getId());
        LastPostInfo lpi = new LastPostInfo();
        lpi.setPostId(last.getId());
        lpi.setTopicId(topic.getId());
        lpi.setUserId(last.getUserId());
        lpi.setUsername(last.getUsername());
        lpi.setPostTime(last.getTime());
        forum.setLastPostInfo(lpi);
        forums.save(forum);
    }

    /** Same effect as legacy admin ForumAction.insertSave: every group gets read access to the new forum/category. */
    private void grantForum(Integer categoryId, Integer forumId) {
        for (Group g : groups.findAll()) {
            addValue(g, SecurityConstants.PERM_CATEGORY, String.valueOf(categoryId));
            addValue(g, SecurityConstants.PERM_FORUM, String.valueOf(forumId));
            groups.save(g);
        }
    }

    private static void addValue(Group g, String roleName, String value) {
        Role role = g.getRoles().stream().filter(r -> roleName.equals(r.getName())).findFirst().orElseGet(() -> {
            Role r = new Role();
            r.setName(roleName);
            g.getRoles().add(r);
            return r;
        });
        if (!role.getValues().contains(value)) {
            role.getValues().add(value);
        }
    }
}
