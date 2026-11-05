package net.jforum.moderation;

import java.time.Instant;
import java.util.List;

import net.jforum.domain.Forum;
import net.jforum.domain.ModerationLog;
import net.jforum.domain.ModerationLogType;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicStatus;
import net.jforum.domain.User;
import net.jforum.security.SecurityConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ModerationIntegrationTest extends Track4Fixtures {

    private Topic topic;
    private Topic other;

    @BeforeEach
    void data() {
        Instant now = Instant.now();
        forum(2, "Second forum");
        forum(3, "Unmoderated by admin");
        grant(GENERAL, SecurityConstants.PERM_FORUM, "2", "3");
        grant(ADMIN_GROUP, SecurityConstants.PERM_FORUM, "2", "3");
        grant(ADMIN_GROUP, SecurityConstants.PERM_MODERATION_FORUMS, "2");
        member(10, "bob", GENERAL);
        topic = topic(100, 2, "Moderate me", 10, now.minusSeconds(60));
        addPost(1000, topic, 10, "bob", "Moderate me", "first message", false, now.minusSeconds(60));
        addPost(1001, topic, 2, "Admin", "Re: Moderate me", "reply", false, now.minusSeconds(30));
        other = topic(101, 3, "Out of scope", 10, now);
        addPost(1002, other, 10, "bob", "Out of scope", "hello", false, now);
        mongo.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
            org.springframework.data.mongodb.core.query.Criteria.where("_id").is(10)),
            org.springframework.data.mongodb.core.query.Update.update("totalPosts", 2), User.class);
        moderation.refreshTopic(100);
        moderation.refreshForum(2);
        moderation.refreshForum(3);
    }

    @org.springframework.beans.factory.annotation.Autowired
    ModerationService moderation;

    @Test
    void lockAndUnlockWritesModerationLog() throws Exception {
        mvc.perform(post("/moderation/doModeration.page").with(admin()).with(csrf())
                .param("topic_id", "100").param("topicLock", "1").param("log_description", "flame war")
                .param("returnUrl", "/forums/show/2.page"))
            .andExpect(status().isOk()).andExpect(view().name("moderation_done"))
            .andExpect(content().string(containsString("/forums/show/2.page")));
        assertThat(mongo.findById(100, Topic.class).getStatus()).isEqualTo(TopicStatus.LOCKED);

        mvc.perform(post("/moderation/doModeration.page").with(admin()).with(csrf())
                .param("topic_id", "100").param("topicUnlock", "1"))
            .andExpect(status().isOk());
        assertThat(mongo.findById(100, Topic.class).getStatus()).isEqualTo(TopicStatus.UNLOCKED);

        List<ModerationLog> logs = mongo.findAll(ModerationLog.class);
        assertThat(logs).extracting(ModerationLog::getType).containsExactlyInAnyOrder(ModerationLogType.LOCK, ModerationLogType.UNLOCK);
        assertThat(logs).filteredOn(l -> l.getType() == ModerationLogType.LOCK).first()
            .satisfies(l -> {
                assertThat(l.getDescription()).isEqualTo("flame war");
                assertThat(l.getUsername()).isEqualTo("Admin");
                assertThat(l.getTopicId()).isEqualTo(100);
                assertThat(l.getPostUserId()).isEqualTo(10);
            });

        mvc.perform(get("/moderation/showActivityLog.page").with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("flame war")))
            .andExpect(content().string(containsString("LOCK")));
    }

    @Test
    void usersWithoutModerationRightsAreDenied() throws Exception {
        mvc.perform(post("/moderation/doModeration.page").with(as(10, "bob")).with(csrf())
                .param("topic_id", "100").param("topicLock", "1"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/moderation/showActivityLog.page").with(as(10, "bob")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/moderation/doModeration.page").with(csrf()).param("topic_id", "100").param("topicRemove", "1"))
            .andExpect(status().isForbidden());
        assertThat(mongo.findById(100, Topic.class)).isNotNull();
        assertThat(mongo.findById(100, Topic.class).getStatus()).isEqualTo(TopicStatus.UNLOCKED);
    }

    @Test
    void moderatorScopeIsLimitedToPermModerationForums() throws Exception {
        mvc.perform(post("/moderation/doModeration.page").with(admin()).with(csrf())
                .param("topic_id", "101").param("topicLock", "1"))
            .andExpect(status().isForbidden());
        assertThat(mongo.findById(101, Topic.class).getStatus()).isEqualTo(TopicStatus.UNLOCKED);
    }

    @Test
    void operationSpecificPermissionIsRequired() throws Exception {
        grant(GENERAL, SecurityConstants.PERM_MODERATION);
        grant(GENERAL, SecurityConstants.PERM_MODERATION_FORUMS, "2");
        // bob is now a moderator of forum 2 but has neither remove nor lock rights
        mvc.perform(post("/moderation/doModeration.page").with(as(10, "bob")).with(csrf())
                .param("topic_id", "100").param("topicRemove", "1"))
            .andExpect(status().isForbidden());
        grant(GENERAL, SecurityConstants.PERM_MODERATION_TOPIC_LOCK_UNLOCK);
        mvc.perform(post("/moderation/doModeration.page").with(as(10, "bob")).with(csrf())
                .param("topic_id", "100").param("topicLock", "1"))
            .andExpect(status().isOk());
        assertThat(mongo.findById(100, Topic.class).getStatus()).isEqualTo(TopicStatus.LOCKED);
    }

    @Test
    void moveTopicUpdatesPostsAndForumCounters() throws Exception {
        mvc.perform(post("/moderation/doModeration.page").with(admin()).with(csrf())
                .param("topic_id", "100").param("topicMove", "1").param("returnUrl", "/forums/show/2.page"))
            .andExpect(status().isOk()).andExpect(view().name("moderation_move"))
            .andExpect(content().string(containsString("name=\"to_forum\"")));

        mvc.perform(post("/moderation/moveTopic.page").with(admin()).with(csrf())
                .param("topics", "100").param("forum_id", "2").param("to_forum", "1").param("log_description", "wrong forum"))
            .andExpect(status().isOk()).andExpect(view().name("moderation_done"));

        assertThat(mongo.findById(100, Topic.class).getForumId()).isEqualTo(1);
        assertThat(mongo.findAll(Post.class)).filteredOn(p -> p.getTopicId() == 100).allMatch(p -> p.getForumId() == 1);
        Forum from = mongo.findById(2, Forum.class);
        Forum to = mongo.findById(1, Forum.class);
        assertThat(from.getTotalTopics()).isZero();
        assertThat(from.getTotalPosts()).isZero();
        assertThat(from.getLastPostInfo()).isNull();
        assertThat(to.getTotalTopics()).isEqualTo(2);
        assertThat(to.getTotalPosts()).isEqualTo(3);
        assertThat(mongo.findAll(ModerationLog.class)).singleElement()
            .satisfies(l -> assertThat(l.getType()).isEqualTo(ModerationLogType.MOVE));
    }

    @Test
    void moveToInvalidForumIsRejected() throws Exception {
        mvc.perform(post("/moderation/moveTopic.page").with(admin()).with(csrf())
                .param("topics", "100").param("forum_id", "2").param("to_forum", "999"))
            .andExpect(status().isOk()).andExpect(content().string(not(containsString("successful"))));
        assertThat(mongo.findById(100, Topic.class).getForumId()).isEqualTo(2);
    }

    @Test
    void deleteTopicRemovesPostsAndUpdatesCounters() throws Exception {
        assertThat(mongo.findById(2, Forum.class).getTotalPosts()).isEqualTo(2);
        mvc.perform(post("/moderation/doModeration.page").with(admin()).with(csrf())
                .param("topic_id", "100").param("topicRemove", "1").param("log_description", "spam"))
            .andExpect(status().isOk());
        assertThat(mongo.findById(100, Topic.class)).isNull();
        assertThat(mongo.findAll(Post.class)).noneMatch(p -> p.getTopicId() == 100);
        Forum f = mongo.findById(2, Forum.class);
        assertThat(f.getTotalTopics()).isZero();
        assertThat(f.getTotalPosts()).isZero();
        assertThat(mongo.findById(10, User.class).getTotalPosts()).isEqualTo(1);
        assertThat(mongo.findAll(ModerationLog.class)).singleElement().satisfies(l -> {
            assertThat(l.getType()).isEqualTo(ModerationLogType.DELETE);
            assertThat(l.getOriginalMessage()).isEqualTo("first message");
        });
    }

    @Test
    void deletePostKeepsTopicConsistent() {
        moderation.deletePost(1001, ModerationService.Moderator.of(mongo.findById(2, User.class), "dup"));
        Topic t = mongo.findById(100, Topic.class);
        assertThat(t.getReplies()).isZero();
        assertThat(t.getLastPostId()).isEqualTo(1000);
        assertThat(mongo.findById(2, Forum.class).getTotalPosts()).isEqualTo(1);
    }

    @Test
    void approveQueue() throws Exception {
        addPost(1003, topic, 10, "bob", "Re: pending", "please approve", true, Instant.now());
        addPost(1004, topic, 10, "bob", "Re: spam", "buy now", true, Instant.now().plusSeconds(1));

        mvc.perform(get("/moderation/pending.page").with(admin()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Second forum")));
        mvc.perform(get("/moderation/pendingView/2.page").with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("please approve")))
            .andExpect(content().string(containsString("status_1003")));
        mvc.perform(get("/moderation/pendingView/2.page").with(as(10, "bob"))).andExpect(status().isForbidden());

        mvc.perform(post("/moderation/pendingSave.page").with(admin()).with(csrf())
                .param("forum_id", "2").param("status_1003", "aprove").param("status_1004", "reject"))
            .andExpect(status().is3xxRedirection());

        assertThat(mongo.findById(1003, Post.class).isModerate()).isFalse();
        assertThat(mongo.findById(1004, Post.class)).isNull();
        Topic t = mongo.findById(100, Topic.class);
        assertThat(t.getReplies()).isEqualTo(2);
        assertThat(t.getLastPostId()).isEqualTo(1003);
        Forum f = mongo.findById(2, Forum.class);
        assertThat(f.getTotalPosts()).isEqualTo(3);
        assertThat(f.getLastPostInfo().getPostId()).isEqualTo(1003);
        assertThat(mongo.findById(10, User.class).getTotalPosts()).isEqualTo(3);
        assertThat(mongo.findAll(ModerationLog.class)).extracting(ModerationLog::getType)
            .containsExactlyInAnyOrder(ModerationLogType.APPROVE, ModerationLogType.REJECT);
    }

    @Test
    void returnUrlMustBeLocal() {
        assertThat(ModerationController.safeReturnUrl("https://evil.example")).isEqualTo("/forums/list.page");
        assertThat(ModerationController.safeReturnUrl("//evil.example")).isEqualTo("/forums/list.page");
        assertThat(ModerationController.safeReturnUrl("/forums/show/2.page")).isEqualTo("/forums/show/2.page");
    }

    @Test
    void legacyUrlIsForwarded() throws Exception {
        mvc.perform(post("/jforum.page").with(admin()).with(csrf())
                .param("module", "moderation").param("action", "doModeration")
                .param("topic_id", "100").param("topicLock", "1"))
            .andExpect(r -> assertThat(r.getResponse().getForwardedUrl()).startsWith("/moderation/doModeration.page?topic_id=100&topicLock=1"));
    }
}
