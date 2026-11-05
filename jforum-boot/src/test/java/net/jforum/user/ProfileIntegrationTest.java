package net.jforum.user;

import static net.jforum.user.UserTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import net.jforum.AbstractIntegrationTest;
import net.jforum.config.ForumConfig;
import net.jforum.domain.AvatarType;
import net.jforum.domain.User;
import net.jforum.repository.UserRepository;
import net.jforum.support.SequenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class ProfileIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private SequenceService sequences;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private ForumConfig config;

    @Autowired
    private net.jforum.post.PostService postService;

    private User mario;

    @BeforeEach
    void createUser() {
        mario = UserTestSupport.createUser(users, sequences, encoder, "Mario", "pw-mario");
    }

    private MockMultipartHttpServletRequestBuilder editSave() {
        MockMultipartHttpServletRequestBuilder b = multipart("/user/editSave.page");
        b.param("user_id", String.valueOf(mario.getId()))
            .param("viewemail", "1").param("hideonline", "0").param("notifyreply", "1").param("notify_always", "0")
            .param("notify_text", "0").param("notifypm", "0").param("attachsig", "1").param("allowhtml", "0")
            .param("allowbbcode", "1").param("allowsmilies", "1").param("language", "it_IT");
        b.with(csrf()).with(as(mario));
        return b;
    }

    private String postAvatar(String contextPath) {
        net.jforum.domain.Post post = new net.jforum.domain.Post();
        post.setUserId(mario.getId());
        post.setText("hi");
        return postService.views(java.util.List.of(post), 0, false,
            new net.jforum.security.PermissionControl(java.util.Map.of()), contextPath).get(0).getAuthor().getAvatarUrl();
    }

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void profileShowsPublicInformationAndRank() throws Exception {
        mario.setFrom("Roma");
        mario.setSignature("[b]Ciao[/b]");
        mario.setViewEmail(false);
        users.save(mario);
        mvc.perform(get("/user/profile/" + mario.getId() + ".page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Mario")))
            .andExpect(content().string(containsString("Roma")))
            .andExpect(content().string(containsString("Stranger")))
            .andExpect(content().string(containsString("<b>Ciao</b>")))
            .andExpect(content().string(not(containsString("mario@example.com"))));
        mvc.perform(get("/user/profile/999.page")).andExpect(status().isNotFound());
        mvc.perform(get("/jforum.page").param("module", "user").param("action", "profile").param("user_id", "2"))
            .andExpect(redirectedUrl("/user/profile/2.page"));
    }

    @Test
    void onlyTheOwnerCanEdit() throws Exception {
        mvc.perform(get("/user/edit/" + mario.getId() + ".page")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/user/edit/2.page").with(as(mario)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("name=\"current_password\""))));
        mvc.perform(get("/user/edit/" + mario.getId() + ".page").with(as(mario)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"current_password\"")))
            .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")));
        mvc.perform(multipart("/user/editSave.page").param("user_id", "2").param("location", "hacked").with(csrf()).with(as(mario)))
            .andExpect(status().isOk());
        assertThat(users.findById(2).orElseThrow().getFrom()).isNull();
    }

    @Test
    void editSavesProfileFieldsAndPreferences() throws Exception {
        mvc.perform(editSave().param("location", "Milano").param("website", "example.org").param("occupation", "Dev")
                .param("signature", "Saluti <script>x</script>").param("biography", "Bio"))
            .andExpect(redirectedUrl("/user/editDone/" + mario.getId() + ".page"));
        User u = users.findById(mario.getId()).orElseThrow();
        assertThat(u.getFrom()).isEqualTo("Milano");
        assertThat(u.getWebsite()).isEqualTo("http://example.org");
        assertThat(u.getOccupation()).isEqualTo("Dev");
        assertThat(u.getSignature()).doesNotContain("<script>");
        assertThat(u.isViewEmail()).isTrue();
        assertThat(u.isNotifyPrivateMessages()).isFalse();
        assertThat(u.getLang()).isEqualTo("it_IT");
        mvc.perform(get("/user/editDone/" + mario.getId() + ".page").with(as(mario)))
            .andExpect(content().string(containsString("alert-success")));
    }

    @Test
    void changePasswordRequiresCurrentPassword() throws Exception {
        mvc.perform(editSave().param("current_password", "wrong").param("new_password", "nuova").param("password_confirm", "nuova"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("alert-error")));
        mvc.perform(editSave().param("current_password", "pw-mario").param("new_password", "nuova").param("password_confirm", "nuova")
                .param("email", "mario@new.example"))
            .andExpect(redirectedUrl("/user/editDone/" + mario.getId() + ".page"));
        assertThat(users.findById(mario.getId()).orElseThrow().getEmail()).isEqualTo("mario@new.example");
        mvc.perform(formLogin("/user/validateLogin.page").user("Mario").password("nuova")).andExpect(authenticated());
    }

    @Test
    void avatarUploadIsResizedStoredInGridFsAndServed() throws Exception {
        config.set("avatar.maxKbSize", "200");
        mvc.perform(editSave().file(new MockMultipartFile("avatar", "me.png", "image/png", png(400, 200))))
            .andExpect(redirectedUrl("/user/editDone/" + mario.getId() + ".page"));
        User u = users.findById(mario.getId()).orElseThrow();
        assertThat(u.getAvatarType()).isEqualTo(AvatarType.UPLOAD);
        assertThat(u.getAvatarUrl()).isEqualTo("/user/avatar/" + mario.getId() + ".page");
        assertThat(mongo.getCollection("fs.files").countDocuments()).isEqualTo(1);
        assertThat(postAvatar("/ctx")).isEqualTo("/ctx/user/avatar/" + mario.getId() + ".page");

        byte[] served = mvc.perform(get("/user/avatar/" + mario.getId() + ".page"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andReturn().getResponse().getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(img.getWidth()).isEqualTo(150);
        assertThat(img.getHeight()).isEqualTo(75);

        mvc.perform(editSave().param("avatardel", "1")).andExpect(status().is3xxRedirection());
        assertThat(users.findById(mario.getId()).orElseThrow().getAvatarUrl()).isNull();
        assertThat(mongo.getCollection("fs.files").countDocuments()).isZero();
        mvc.perform(get("/user/avatar/" + mario.getId() + ".page")).andExpect(status().isNotFound());
    }

    @Test
    void avatarLimitsGalleryAndExternalUrl() throws Exception {
        config.set("avatar.maxKbSize", "1");
        mvc.perform(editSave().file(new MockMultipartFile("avatar", "big.png", "image/png", new byte[5000])))
            .andExpect(content().string(containsString("alert-error")));
        mvc.perform(editSave().file(new MockMultipartFile("avatar", "x.exe", "application/octet-stream", new byte[10])))
            .andExpect(content().string(containsString("alert-error")));

        mvc.perform(editSave().param("avatarGallery", "avatar_02.png")).andExpect(status().is3xxRedirection());
        assertThat(users.findById(mario.getId()).orElseThrow().getAvatarUrl()).isEqualTo("/images/avatar/avatar_02.png");
        assertThat(postAvatar("")).isEqualTo("/images/avatar/avatar_02.png");
        mvc.perform(get("/images/avatar/avatar_02.png")).andExpect(status().isOk());

        mvc.perform(editSave().param("avatarUrl", "ftp://example.org/a.png")).andExpect(content().string(containsString("alert-error")));
        mvc.perform(editSave().param("avatarUrl", "https://example.org/a.png")).andExpect(status().is3xxRedirection());
        User u = users.findById(mario.getId()).orElseThrow();
        assertThat(u.getAvatarType()).isEqualTo(AvatarType.EXTERNAL);
        assertThat(u.getAvatarUrl()).isEqualTo("https://example.org/a.png");
    }

    @Test
    void userListIsPaginated() throws Exception {
        config.set("usersPerPage", "2");
        UserTestSupport.createUser(users, sequences, encoder, "Zeta", "pw");
        mvc.perform(get("/user/list.page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Anonymous")))
            .andExpect(content().string(containsString("/user/list/2.page")))
            .andExpect(content().string(not(containsString(">Zeta<"))));
        mvc.perform(get("/user/list/2.page"))
            .andExpect(content().string(containsString(">Mario<")))
            .andExpect(content().string(containsString(">Zeta<")));
        mvc.perform(get("/user/listGroup/2.page"))
            .andExpect(content().string(containsString(">Admin<")))
            .andExpect(content().string(not(containsString(">Mario<"))));
    }
}
