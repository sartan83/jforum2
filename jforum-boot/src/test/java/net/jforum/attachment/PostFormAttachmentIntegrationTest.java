package net.jforum.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import net.jforum.domain.Attachment;
import net.jforum.domain.AttachmentExtension;
import net.jforum.domain.Post;
import net.jforum.moderation.Track4Fixtures;
import net.jforum.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Uploads through the new topic / reply form (legacy file_N / comment_N fields) and the topic page wiring. */
class PostFormAttachmentIntegrationTest extends Track4Fixtures {

    @BeforeEach
    void allowAttachments() {
        grant(ADMIN_GROUP, SecurityConstants.PERM_ATTACHMENTS_ENABLED, "1");
    }

    private static MockMultipartFile file(String name, String body) {
        return new MockMultipartFile("file_0", name, "text/plain", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void newTopicWithAttachmentShowsItOnTopicPage() throws Exception {
        mvc.perform(get("/posts/insert/1.page").with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("multipart/form-data")))
            .andExpect(content().string(containsString("name=\"file_0\"")));

        mvc.perform(multipart("/posts/insertSave.page").file(file("notes.txt", "hello"))
                .param("forum_id", "1").param("subject", "With a file").param("message", "see attachment")
                .param("total_files", "1").param("comment_0", "my notes").with(admin()).with(csrf()))
            .andExpect(status().is3xxRedirection());

        Attachment a = mongo.findAll(Attachment.class).get(0);
        Post post = mongo.findById(a.getPostId(), Post.class);
        assertThat(post.getSubject()).isEqualTo("With a file");
        assertThat(post.isHasAttachments()).isTrue();
        assertThat(a.getDescription()).isEqualTo("my notes");

        mvc.perform(get("/posts/list/" + post.getTopicId() + ".page").with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/posts/downloadAttach/" + a.getId() + ".page")))
            .andExpect(content().string(containsString("/moderation/doModeration.page")));
    }

    @Test
    void rejectedAttachmentKeepsFormAndCreatesNoPost() throws Exception {
        AttachmentExtension exe = new AttachmentExtension();
        exe.setId(1);
        exe.setExtension("exe");
        exe.setAllow(false);
        mongo.save(exe);
        long before = mongo.count(new org.springframework.data.mongodb.core.query.Query(), Post.class);

        mvc.perform(multipart("/posts/insertSave.page").file(file("virus.exe", "MZ"))
                .param("forum_id", "1").param("subject", "Bad").param("message", "x").param("total_files", "1")
                .with(admin()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(".exe")));

        assertThat(mongo.count(new org.springframework.data.mongodb.core.query.Query(), Post.class)).isEqualTo(before);
        assertThat(mongo.findAll(Attachment.class)).isEmpty();
    }

    @Test
    void anonymousTopicPageHasNoModerationBox() throws Exception {
        mvc.perform(get("/posts/list/1.page"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/moderation/doModeration.page"))));
    }
}
