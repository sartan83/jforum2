package net.jforum.pm;

import static net.jforum.user.UserTestSupport.as;
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
import net.jforum.domain.PrivateMessage;
import net.jforum.domain.PrivateMessageType;
import net.jforum.domain.User;
import net.jforum.repository.PrivateMessageRepository;
import net.jforum.repository.UserRepository;
import net.jforum.support.SequenceService;
import net.jforum.user.UserTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class PrivateMessageIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private PrivateMessageRepository messages;

    @Autowired
    private SequenceService sequences;

    @Autowired
    private PasswordEncoder encoder;

    private User admin;
    private User luigi;

    @BeforeEach
    void setUp() {
        admin = users.findById(2).orElseThrow();
        luigi = UserTestSupport.createUser(users, sequences, encoder, "Luigi", "pw");
    }

    private void sendAdminToLuigi(String subject) throws Exception {
        mvc.perform(post("/pm/sendSave.page").with(csrf()).with(as(admin))
                .param("toUsername", "luigi").param("toUserId", "").param("subject", subject).param("message", "Hello [b]Luigi[/b] :)"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Your message was successfully sent")));
    }

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mvc.perform(get("/pm/inbox.page")).andExpect(redirectedUrl("/user/login.page?returnPath=/pm/inbox.page"));
        mvc.perform(get("/pm/read/1.page")).andExpect(status().is3xxRedirection());
    }

    @Test
    void sendStoresTwoCopiesAndUpdatesUnreadCounter() throws Exception {
        mvc.perform(get("/pm/sendTo/" + luigi.getId() + ".page").with(as(admin)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("value=\"Luigi\"")));
        sendAdminToLuigi("Ciao");

        List<PrivateMessage> all = messages.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(PrivateMessage::getType).containsExactlyInAnyOrder(PrivateMessageType.SENT, PrivateMessageType.NEW);
        assertThat(all).allSatisfy(pm -> {
            assertThat(pm.getFromUserId()).isEqualTo(2);
            assertThat(pm.getToUserId()).isEqualTo(luigi.getId());
        });
        assertThat(users.findById(luigi.getId()).orElseThrow().getUnreadPrivateMessages()).isEqualTo(1);

        mvc.perform(get("/forums/list.page").with(as(luigi)))
            .andExpect(content().string(containsString("<span class=\"badge\">1</span>")));
        mvc.perform(get("/pm/sentbox.page").with(as(admin)))
            .andExpect(content().string(containsString("Ciao")))
            .andExpect(content().string(containsString("Luigi")));
        mvc.perform(get("/pm/inbox.page").with(as(admin))).andExpect(content().string(not(containsString("Ciao"))));

        PrivateMessage inboxCopy = all.stream().filter(pm -> pm.getType() == PrivateMessageType.NEW).findFirst().orElseThrow();
        mvc.perform(get("/pm/inbox.page").with(as(luigi)))
            .andExpect(content().string(containsString("/pm/read/" + inboxCopy.getId() + ".page")))
            .andExpect(content().string(containsString("unread")));
        mvc.perform(get("/pm/read/" + inboxCopy.getId() + ".page").with(as(luigi)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("<b>Luigi</b>")))
            .andExpect(content().string(containsString("smilie")));
        assertThat(messages.findById(inboxCopy.getId()).orElseThrow().getType()).isEqualTo(PrivateMessageType.READ);
        assertThat(users.findById(luigi.getId()).orElseThrow().getUnreadPrivateMessages()).isZero();
        mvc.perform(get("/forums/list.page").with(as(luigi))).andExpect(content().string(not(containsString("class=\"badge\""))));
    }

    @Test
    void readIsDeniedToOtherUsers() throws Exception {
        sendAdminToLuigi("Secret");
        User peach = UserTestSupport.createUser(users, sequences, encoder, "Peach", "pw");
        for (PrivateMessage pm : messages.findAll()) {
            mvc.perform(get("/pm/read/" + pm.getId() + ".page").with(as(peach)))
                .andExpect(content().string(containsString("permission to read this message")))
                .andExpect(content().string(not(containsString("Secret"))));
        }
    }

    @Test
    void replyAndQuotePrefillTheForm() throws Exception {
        sendAdminToLuigi("Domanda");
        PrivateMessage inbox = messages.findAll().stream().filter(pm -> pm.getType() == PrivateMessageType.NEW).findFirst().orElseThrow();
        mvc.perform(get("/pm/reply/" + inbox.getId() + ".page").with(as(luigi)))
            .andExpect(content().string(containsString("value=\"Re:Domanda\"")))
            .andExpect(content().string(containsString("value=\"Admin\"")));
        mvc.perform(get("/pm/quote/" + inbox.getId() + ".page").with(as(luigi)))
            .andExpect(content().string(containsString("[quote=Admin]Hello [b]Luigi[/b] :)[/quote]")));
        mvc.perform(post("/pm/sendSave.page").with(csrf()).with(as(luigi))
                .param("toUserId", "2").param("subject", "Re:Domanda").param("message", "Risposta"))
            .andExpect(content().string(containsString("successfully sent")));
        assertThat(users.findById(2).orElseThrow().getUnreadPrivateMessages()).isEqualTo(1);
    }

    @Test
    void previewAndUnknownRecipient() throws Exception {
        mvc.perform(post("/pm/sendSave.page").with(csrf()).with(as(admin))
                .param("toUsername", "Luigi").param("subject", "S").param("message", "[i]anteprima[/i]").param("preview", "1"))
            .andExpect(content().string(containsString("<i>anteprima</i>")));
        assertThat(messages.count()).isZero();
        mvc.perform(post("/pm/sendSave.page").with(csrf()).with(as(admin))
                .param("toUsername", "ghost").param("subject", "S").param("message", "x"))
            .andExpect(content().string(containsString("Could not determine the user id")));
    }

    @Test
    void deleteOnlyOwnCopiesAndAdjustCounter() throws Exception {
        sendAdminToLuigi("Uno");
        PrivateMessage inbox = messages.findAll().stream().filter(pm -> pm.getType() == PrivateMessageType.NEW).findFirst().orElseThrow();
        PrivateMessage sent = messages.findAll().stream().filter(pm -> pm.getType() == PrivateMessageType.SENT).findFirst().orElseThrow();
        mvc.perform(post("/pm/delete.page").with(csrf()).with(as(admin)).param("id", String.valueOf(inbox.getId())))
            .andExpect(content().string(containsString("Message deletion done")));
        assertThat(messages.existsById(inbox.getId())).isTrue();

        mvc.perform(post("/pm/delete.page").with(csrf()).with(as(luigi)).param("id", String.valueOf(inbox.getId())));
        assertThat(messages.existsById(inbox.getId())).isFalse();
        assertThat(users.findById(luigi.getId()).orElseThrow().getUnreadPrivateMessages()).isZero();

        mvc.perform(post("/pm/delete.page").with(csrf()).with(as(admin)).param("id", String.valueOf(sent.getId())));
        assertThat(messages.count()).isZero();
    }

    @Test
    void findUser() throws Exception {
        mvc.perform(get("/pm/findUser.page").param("username", "lui").with(as(admin)))
            .andExpect(content().string(containsString("data-name=\"Luigi\"")));
    }
}
