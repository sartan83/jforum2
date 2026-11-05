package net.jforum.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import net.jforum.AbstractIntegrationTest;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Forum;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicStatus;
import net.jforum.domain.TopicType;
import net.jforum.domain.User;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.ForumWatchRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.TopicWatchRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.PermissionService;
import net.jforum.security.SecurityConstants;
import net.jforum.support.SequenceService;
import net.jforum.topic.TopicService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;

class PostFlowsIntegrationTest extends AbstractIntegrationTest {

    @Autowired PostService postService;
    @Autowired TopicService topicService;
    @Autowired ForumRepository forums;
    @Autowired TopicRepository topics;
    @Autowired PostRepository posts;
    @Autowired UserRepository users;
    @Autowired TopicWatchRepository topicWatches;
    @Autowired ForumWatchRepository forumWatches;
    @Autowired PermissionService permissions;
    @Autowired SequenceService sequences;
    @Autowired ForumConfig config;

    private User admin() {
        return users.findById(2).orElseThrow();
    }

    /** Creates a forum in category 1 and, when visible, grants perm_forum and the posting roles to both groups. */
    private Forum createForum(String name, boolean visible) {
        Forum f = new Forum();
        f.setId((int) sequences.next("forums"));
        f.setCategoryId(1);
        f.setName(name);
        f.setDescription(name + " description");
        forums.save(f);
        if (visible) {
            for (String role : List.of(SecurityConstants.PERM_FORUM, SecurityConstants.PERM_ANONYMOUS_POST,
                SecurityConstants.PERM_READ_ONLY_FORUMS, SecurityConstants.PERM_REPLY_ONLY, SecurityConstants.PERM_REPLY_WITHOUT_MODERATION)) {
                permissions.grant(role, String.valueOf(f.getId()), List.of(1, 2));
            }
        }
        permissions.invalidate();
        return f;
    }

    private Post newTopic(Forum forum, String subject, TopicType type) {
        PostCommand c = new PostCommand();
        c.setSubject(subject);
        c.setMessage("Body of " + subject);
        c.setTopicType(type);
        User u = admin();
        return postService.insert(c, forum, null, u, true, permissions.forUser(u), "127.0.0.1");
    }

    private Post reply(Topic topic, String text) {
        PostCommand c = new PostCommand();
        c.setMessage(text);
        User u = admin();
        return postService.insert(c, forums.findById(topic.getForumId()).orElseThrow(), topic, u, true, permissions.forUser(u), "127.0.0.1");
    }

    private static int idFromRedirect(MvcResult r) {
        String url = r.getResponse().getRedirectedUrl();
        return Integer.parseInt(url.substring(url.indexOf('#') + 1));
    }

    @Test
    void forumCreatedViaServiceIsListedAndShown() throws Exception {
        Forum f = createForum("Second Forum", true);
        Post p = newTopic(f, "Service topic", TopicType.NORMAL);
        mvc.perform(get("/forums/list.page")).andExpect(content().string(containsString("Second Forum")));
        mvc.perform(get("/forums/show/" + f.getId() + ".page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Service topic")))
            .andExpect(content().string(containsString("/posts/list/" + p.getTopicId() + ".page")));
        Forum reloaded = forums.findById(f.getId()).orElseThrow();
        assertThat(reloaded.getTotalTopics()).isEqualTo(1);
        assertThat(reloaded.getTotalPosts()).isEqualTo(1);
        assertThat(reloaded.getLastPostInfo().getPostId()).isEqualTo(p.getId());
    }

    @Test
    @WithUserDetails("Admin")
    void topicCreationReplyQuoteEditAndDelete() throws Exception {
        int adminPosts = admin().getTotalPosts();
        MvcResult created = mvc.perform(post("/posts/insertSave.page").with(csrf())
                .param("forum_id", "1").param("subject", "My new topic").param("message", "Hello [b]world[/b]")
                .param("topic_type", "1").param("notify", "1"))
            .andExpect(status().is3xxRedirection()).andReturn();
        int firstPostId = idFromRedirect(created);
        Post first = posts.findById(firstPostId).orElseThrow();
        Topic topic = topics.findById(first.getTopicId()).orElseThrow();
        assertThat(created.getResponse().getRedirectedUrl()).isEqualTo("/posts/list/" + topic.getId() + ".page#" + firstPostId);
        assertThat(topic.getTitle()).isEqualTo("My new topic");
        assertThat(topic.getType()).isEqualTo(TopicType.STICKY);
        assertThat(topic.getFirstPostId()).isEqualTo(firstPostId);
        assertThat(topicWatches.findByTopicIdAndUserId(topic.getId(), 2)).isPresent();
        assertThat(forums.findById(1).orElseThrow().getTotalTopics()).isEqualTo(2);
        assertThat(admin().getTotalPosts()).isEqualTo(adminPosts + 1);

        mvc.perform(get("/posts/list/" + topic.getId() + ".page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("My new topic")))
            .andExpect(content().string(containsString("world")))
            .andExpect(content().string(not(containsString("[b]world"))));
        assertThat(topics.findById(topic.getId()).orElseThrow().getViews()).isGreaterThanOrEqualTo(1);

        MvcResult replied = mvc.perform(post("/posts/insertSave.page").with(csrf())
                .param("topic_id", String.valueOf(topic.getId())).param("message", "A reply"))
            .andExpect(status().is3xxRedirection()).andReturn();
        int replyId = idFromRedirect(replied);
        topic = topics.findById(topic.getId()).orElseThrow();
        assertThat(topic.getReplies()).isEqualTo(1);
        assertThat(topic.getLastPostId()).isEqualTo(replyId);
        Forum forum = forums.findById(1).orElseThrow();
        assertThat(forum.getLastPostInfo().getPostId()).isEqualTo(replyId);
        assertThat(forum.getTotalPosts()).isEqualTo(3);

        mvc.perform(get("/posts/quote/" + replyId + ".page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("[quote=Admin]A reply[/quote]")));

        mvc.perform(post("/posts/editSave.page").with(csrf())
                .param("post_id", String.valueOf(firstPostId)).param("subject", "Renamed topic").param("message", "Edited text"))
            .andExpect(redirectedUrl("/posts/list/" + topic.getId() + ".page#" + firstPostId));
        Post edited = posts.findById(firstPostId).orElseThrow();
        assertThat(edited.getText()).isEqualTo("Edited text");
        assertThat(edited.getEditCount()).isEqualTo(1);
        assertThat(topics.findById(topic.getId()).orElseThrow().getTitle()).isEqualTo("Renamed topic");

        mvc.perform(post("/posts/delete/" + replyId + ".page").with(csrf()))
            .andExpect(redirectedUrl("/posts/list/" + topic.getId() + ".page"));
        topic = topics.findById(topic.getId()).orElseThrow();
        assertThat(topic.getReplies()).isZero();
        assertThat(topic.getLastPostId()).isEqualTo(firstPostId);
        assertThat(forums.findById(1).orElseThrow().getTotalPosts()).isEqualTo(2);

        mvc.perform(post("/posts/delete/" + firstPostId + ".page").with(csrf()))
            .andExpect(redirectedUrl("/forums/show/1.page"));
        assertThat(topics.findById(topic.getId())).isEmpty();
        forum = forums.findById(1).orElseThrow();
        assertThat(forum.getTotalTopics()).isEqualTo(1);
        assertThat(forum.getLastPostInfo().getPostId()).isEqualTo(1);
        assertThat(admin().getTotalPosts()).isEqualTo(adminPosts);
    }

    @Test
    void blankMessageAndPreviewRerenderTheForm() throws Exception {
        mvc.perform(post("/posts/insertSave.page").with(csrf()).param("forum_id", "1").param("subject", "x").param("message", " "))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Empty message")));
        mvc.perform(post("/posts/insertSave.page").with(csrf()).param("forum_id", "1").param("subject", "x")
                .param("message", "[b]bold[/b]").param("preview", "1"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("post-preview")));
        assertThat(topics.count()).isEqualTo(1);
    }

    @Test
    void permissionDenials() throws Exception {
        Forum hidden = createForum("Hidden Forum", false);
        Post hiddenPost = newTopic(hidden, "Secret", TopicType.NORMAL);
        mvc.perform(get("/forums/show/" + hidden.getId() + ".page")).andExpect(status().isForbidden());
        mvc.perform(get("/posts/list/" + hiddenPost.getTopicId() + ".page")).andExpect(redirectedUrl("/user/login.page"));
        mvc.perform(get("/forums/list.page")).andExpect(content().string(not(containsString("Hidden Forum"))));
        mvc.perform(get("/recentTopics/list.page")).andExpect(content().string(not(containsString("Secret"))));

        // anonymous users may not edit or delete posts
        mvc.perform(get("/posts/edit/1.page")).andExpect(status().isForbidden());
        mvc.perform(post("/posts/delete/1.page").with(csrf())).andExpect(status().isForbidden());

        // perm_anonymous_post
        mvc.perform(get("/posts/insert/1.page")).andExpect(status().isOk());
        permissions.revokeValue(SecurityConstants.PERM_ANONYMOUS_POST, "1");
        permissions.invalidate();
        mvc.perform(get("/posts/insert/1.page")).andExpect(redirectedUrl("/user/login.page"));
        mvc.perform(post("/posts/insertSave.page").with(csrf()).param("topic_id", "1").param("message", "anon"))
            .andExpect(redirectedUrl("/user/login.page"));
        assertThat(posts.count()).isEqualTo(2);
    }

    @Test
    @WithUserDetails("Admin")
    void replyOnlyReadOnlyAndLockedTopics() throws Exception {
        permissions.revokeValue(SecurityConstants.PERM_REPLY_ONLY, "1");
        permissions.invalidate();
        mvc.perform(get("/posts/insert/1.page")).andExpect(content().string(containsString("reply-only")));
        mvc.perform(get("/posts/reply/1.page")).andExpect(status().isOk());

        Topic t = topics.findById(1).orElseThrow();
        t.setStatus(TopicStatus.LOCKED);
        topics.save(t);
        mvc.perform(get("/posts/reply/1.page")).andExpect(content().string(containsString("locked")));
        mvc.perform(post("/posts/insertSave.page").with(csrf()).param("topic_id", "1").param("message", "x"))
            .andExpect(content().string(containsString("locked")));

        permissions.revokeValue(SecurityConstants.PERM_READ_ONLY_FORUMS, "1");
        permissions.invalidate();
        mvc.perform(post("/posts/insertSave.page").with(csrf()).param("topic_id", "1").param("message", "x"))
            .andExpect(redirectedUrl("/posts/list/1.page"));
        assertThat(posts.count()).isEqualTo(1);
    }

    @Test
    void moderatedForumSendsAnonymousRepliesToTheQueue() throws Exception {
        Forum f = forums.findById(1).orElseThrow();
        f.setModerated(true);
        forums.save(f);
        permissions.revokeValue(SecurityConstants.PERM_REPLY_WITHOUT_MODERATION, "1");
        permissions.invalidate();
        mvc.perform(post("/posts/insertSave.page").with(csrf()).param("topic_id", "1").param("message", "needs approval"))
            .andExpect(redirectedUrl("/posts/waitingModeration/1/1.page"));
        assertThat(posts.findByModerateTrue()).hasSize(1);
        assertThat(topics.findById(1).orElseThrow().getReplies()).isZero();
        assertThat(forums.findById(1).orElseThrow().getTotalPosts()).isEqualTo(1);
    }

    @Test
    void paginationOfTopicsAndPosts() throws Exception {
        config.set("postsPerPage", "2");
        config.set("topicsPerPage", "2");
        Topic welcome = topics.findById(1).orElseThrow();
        Post last = null;
        for (int i = 0; i < 4; i++) {
            last = reply(welcome, "reply " + i);
        }
        mvc.perform(get("/posts/list/1.page"))
            .andExpect(content().string(containsString("/posts/list/2/1.page")))
            .andExpect(content().string(containsString("/posts/list/4/1.page")))
            .andExpect(content().string(not(containsString("reply 2"))));
        mvc.perform(get("/posts/list/4/1.page")).andExpect(content().string(containsString("reply 3")));
        mvc.perform(get("/posts/preList/1/" + last.getId() + ".page")).andExpect(redirectedUrl("/posts/list/4/1.page#" + last.getId()));

        Forum f = forums.findById(1).orElseThrow();
        newTopic(f, "Normal topic", TopicType.NORMAL);
        Post sticky = newTopic(f, "Sticky topic", TopicType.STICKY);
        newTopic(f, "Newest normal", TopicType.NORMAL);
        List<Topic> firstPage = topicService.forumTopics(1, 0, 2);
        assertThat(firstPage.get(0).getId()).isEqualTo(sticky.getTopicId());
        mvc.perform(get("/forums/show/1.page"))
            .andExpect(content().string(containsString("Sticky topic")))
            .andExpect(content().string(containsString("/forums/show/2/1.page")));
        mvc.perform(get("/forums/show/2/1.page")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Sticky topic"))));
    }

    @Test
    @WithUserDetails("Admin")
    void pollCreationVotingAndResults() throws Exception {
        mvc.perform(post("/posts/insertSave.page").with(csrf()).param("forum_id", "1").param("subject", "Poll").param("message", "vote")
                .param("poll_label", "Best?").param("poll_option_count", "2").param("poll_option_0", "Only one"))
            .andExpect(content().string(containsString("at least 2 poll options")));
        MvcResult r = mvc.perform(post("/posts/insertSave.page").with(csrf()).param("forum_id", "1").param("subject", "Poll")
                .param("message", "vote").param("poll_label", "Best?").param("poll_option_count", "2")
                .param("poll_option_0", "Mongo").param("poll_option_1", "SQL"))
            .andExpect(status().is3xxRedirection()).andReturn();
        int topicId = posts.findById(idFromRedirect(r)).orElseThrow().getTopicId();
        assertThat(topics.findById(topicId).orElseThrow().getPoll().getOptions()).hasSize(2);
        mvc.perform(get("/posts/list/" + topicId + ".page")).andExpect(content().string(containsString("name=\"poll_option\"")));

        MvcResult forwarded = mvc.perform(post("/jforum.page").with(csrf()).param("module", "posts").param("action", "vote")
            .param("topic_id", String.valueOf(topicId)).param("poll_option", "1")).andReturn();
        assertThat(forwarded.getResponse().getForwardedUrl()).isEqualTo("/posts/vote.page");

        mvc.perform(post("/posts/vote.page").with(csrf()).param("topic_id", String.valueOf(topicId)).param("poll_option", "1"))
            .andExpect(redirectedUrl("/posts/list/" + topicId + ".page"));
        mvc.perform(post("/posts/vote.page").with(csrf()).param("topic_id", String.valueOf(topicId)).param("poll_option", "2"))
            .andExpect(redirectedUrl("/posts/list/" + topicId + ".page"));
        Topic t = topics.findById(topicId).orElseThrow();
        assertThat(t.getPoll().getOptions().get(0).getVoteCount()).isEqualTo(1);
        assertThat(t.getPoll().getOptions().get(1).getVoteCount()).isZero();
        assertThat(t.getPoll().getVoters()).hasSize(1);
        mvc.perform(get("/posts/list/" + topicId + ".page"))
            .andExpect(content().string(not(containsString("name=\"poll_option\""))))
            .andExpect(content().string(containsString("100%")));
    }

    @Test
    @WithUserDetails("Admin")
    void watchesAndTopicLists() throws Exception {
        mvc.perform(get("/posts/watch/1.page")).andExpect(redirectedUrl("/posts/list/1.page"));
        assertThat(topicWatches.findByTopicIdAndUserId(1, 2)).isPresent();
        mvc.perform(get("/posts/unwatch/1.page")).andExpect(status().isOk());
        assertThat(topicWatches.findByTopicIdAndUserId(1, 2)).isEmpty();
        mvc.perform(get("/forums/watchForum/1.page")).andExpect(redirectedUrl("/forums/show/1.page"));
        assertThat(forumWatches.findByForumIdAndUserId(1, 2)).isPresent();
        mvc.perform(get("/forums/unwatchForum/1.page")).andExpect(status().isOk());
        assertThat(forumWatches.findByForumIdAndUserId(1, 2)).isEmpty();

        mvc.perform(get("/recentTopics/list.page")).andExpect(status().isOk()).andExpect(content().string(containsString("Welcome to JForum")));
        mvc.perform(get("/hottestTopics/list.page")).andExpect(status().isOk()).andExpect(content().string(containsString("Welcome to JForum")));
        mvc.perform(get("/forums/newMessages.page")).andExpect(status().isOk());
        mvc.perform(get("/jforum.page").param("module", "posts").param("action", "list").param("topic_id", "1"))
            .andExpect(redirectedUrl("/posts/list/1.page"));
        mvc.perform(get("/posts/list/999.page")).andExpect(status().isNotFound());
        mvc.perform(get("/posts/preList/1/1.page")).andExpect(redirectedUrl("/posts/list/1.page#1"));
    }
}
