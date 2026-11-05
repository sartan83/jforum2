package net.jforum.attachment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import net.jforum.domain.Attachment;
import net.jforum.domain.AttachmentExtension;
import net.jforum.domain.Post;
import net.jforum.domain.QuotaLimit;
import net.jforum.domain.Topic;
import net.jforum.moderation.Track4Fixtures;
import net.jforum.security.SecurityConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttachmentIntegrationTest extends Track4Fixtures {

    @BeforeEach
    void data() {
        member(10, "bob", GENERAL);
        Topic t = topic(100, 1, "With files", 10, Instant.now());
        addPost(1000, t, 10, "bob", "With files", "see attached", false, Instant.now());
    }

    private MockMultipartFile file(String name, String body) {
        return new MockMultipartFile("file", name, "text/plain", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadDownloadRoundTripThroughGridFs() throws Exception {
        mvc.perform(multipart("/attachments/upload/1000.page").file(file("notes.txt", "hello gridfs"))
                .param("comment", "my notes").with(as(10, "bob")).with(csrf()))
            .andExpect(status().is3xxRedirection());

        Attachment a = mongo.findAll(Attachment.class).get(0);
        assertThat(a.getRealFilename()).isEqualTo("notes.txt");
        assertThat(a.getDescription()).isEqualTo("my notes");
        assertThat(a.getFilesize()).isEqualTo(12);
        assertThat(a.getGridFsId()).isNotBlank();
        assertThat(mongo.getCollection("fs.files").countDocuments()).isEqualTo(1);
        assertThat(mongo.findById(1000, Post.class).isHasAttachments()).isTrue();
        assertThat(mongo.findById(100, Topic.class).isHasAttach()).isTrue();

        mvc.perform(get("/attachments/manage/1000.page").with(as(10, "bob")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("notes.txt")));

        mvc.perform(get("/posts/downloadAttach/" + a.getId() + ".page"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("notes.txt")))
            .andExpect(content().string("hello gridfs"));
        mvc.perform(get("/jforum.page?module=posts&action=downloadAttach&attach_id=" + a.getId()))
            .andExpect(status().is3xxRedirection());
        assertThat(mongo.findById(a.getId(), Attachment.class).getDownloadCount()).isEqualTo(1);

        mvc.perform(post("/attachments/delete/" + a.getId() + ".page").with(as(10, "bob")).with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertThat(mongo.findAll(Attachment.class)).isEmpty();
        assertThat(mongo.getCollection("fs.files").countDocuments()).isZero();
        assertThat(mongo.findById(1000, Post.class).isHasAttachments()).isFalse();
    }

    @Test
    void downloadRequiresPermission() throws Exception {
        mvc.perform(multipart("/attachments/upload/1000.page").file(file("a.txt", "x")).with(admin()).with(csrf()));
        Attachment a = mongo.findAll(Attachment.class).get(0);
        // remove download + enabled rights from the anonymous user's group
        var g = mongo.findById(GENERAL, net.jforum.domain.Group.class);
        g.getRoles().removeIf(r -> r.getName().equals(SecurityConstants.PERM_ATTACHMENTS_DOWNLOAD)
            || r.getName().equals(SecurityConstants.PERM_ATTACHMENTS_ENABLED));
        mongo.save(g);
        permissionService.invalidate();
        mvc.perform(get("/posts/downloadAttach/" + a.getId() + ".page")).andExpect(status().isForbidden());
        mvc.perform(get("/posts/downloadAttach/9999.page")).andExpect(status().isNotFound());
        assertThat(mongo.findById(a.getId(), Attachment.class).getDownloadCount()).isZero();
    }

    @Test
    void uploadValidation() throws Exception {
        AttachmentExtension exe = new AttachmentExtension();
        exe.setId(1);
        exe.setExtension("exe");
        exe.setAllow(false);
        mongo.save(exe);
        mvc.perform(multipart("/attachments/upload/1000.page").file(file("virus.exe", "MZ")).with(as(10, "bob")).with(csrf()))
            .andExpect(flash().attribute("error", containsString(".exe")));

        QuotaLimit q = new QuotaLimit();
        q.setId(1);
        q.setSize(1);
        q.setType(1);
        q.getGroupIds().add(GENERAL);
        mongo.save(q);
        mvc.perform(multipart("/attachments/upload/1000.page").file(file("big.txt", "x".repeat(2048))).with(as(10, "bob")).with(csrf()))
            .andExpect(flash().attribute("error", containsString("1 kb")));

        mvc.perform(multipart("/attachments/upload/1000.page")
                .file(file("1.txt", "1")).file(file("2.txt", "2")).file(file("3.txt", "3")).file(file("4.txt", "4"))
                .with(as(10, "bob")).with(csrf()))
            .andExpect(flash().attributeExists("error"));
        assertThat(mongo.findAll(Attachment.class)).isEmpty();

        // someone else cannot manage bob's post
        member(11, "eve", GENERAL);
        mvc.perform(get("/attachments/manage/1000.page").with(as(11, "eve"))).andExpect(status().isForbidden());
    }

    @Test
    void attachmentPermissionIsForumScoped() {
        var group = mongo.findById(GENERAL, net.jforum.domain.Group.class);
        group.getRoles().removeIf(r -> r.getName().equals(SecurityConstants.PERM_ATTACHMENTS_ENABLED));
        mongo.save(group);
        permissionService.invalidate();
        assertThat(service.canAttach(permissionService.forUser(mongo.findById(10, net.jforum.domain.User.class)), 1)).isFalse();
        List<Attachment> none = mongo.findAll(Attachment.class);
        assertThat(none).isEmpty();
    }

    @org.springframework.beans.factory.annotation.Autowired
    AttachmentService service;
}
