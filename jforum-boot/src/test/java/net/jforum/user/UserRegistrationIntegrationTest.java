package net.jforum.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jforum.AbstractIntegrationTest;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Banlist;
import net.jforum.domain.User;
import net.jforum.repository.BanlistRepository;
import net.jforum.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.servlet.http.Cookie;

import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties = "management.health.mail.enabled=false")
class UserRegistrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private BanlistRepository banlist;

    @Autowired
    private ForumConfig config;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoSpyBean
    private CaptchaService captcha;

    /** Carries the Spring Session cookie between requests, like a browser. */
    private class Browser {
        private Cookie cookie;

        ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
            if (cookie != null) {
                builder.cookie(cookie);
            }
            ResultActions actions = mvc.perform(builder);
            Cookie c = actions.andReturn().getResponse().getCookie("SESSION");
            if (c != null) {
                cookie = c;
            }
            return actions;
        }
    }

    private Browser acceptedAgreement() throws Exception {
        Browser session = new Browser();
        session.perform(get("/user/insert.page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/user/acceptAgreement.page")));
        session.perform(get("/user/acceptAgreement.page")).andExpect(redirectedUrl("/user/insert.page"));
        session.perform(get("/user/insert.page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"password_confirm\"")));
        return session;
    }

    private MvcResult register(Browser session, String username, String email, String password, String confirm) throws Exception {
        return session.perform(post("/user/insertSave.page").with(csrf())
                .param("username", username).param("email", email).param("password", password).param("password_confirm", confirm))
            .andReturn();
    }

    @Test
    void registrationCreatesActiveUserLogsInAndAllowsLogin() throws Exception {
        Browser session = acceptedAgreement();
        session.perform(post("/user/insertSave.page").with(csrf())
                .param("username", "newbie").param("email", "newbie@example.com").param("password", "secret1").param("password_confirm", "secret1"))
            .andExpect(redirectedUrl("/user/registrationComplete.page"));
        session.perform(get("/forums/list.page")).andExpect(content().string(containsString("[newbie]")));

        User u = users.findByUsernameIgnoreCase("newbie").orElseThrow();
        assertThat(u.isActive()).isTrue();
        assertThat(u.getId()).isGreaterThan(2);
        assertThat(u.getGroupIds()).containsExactly(1);
        assertThat(u.getPassword()).startsWith("{bcrypt}");

        session.perform(get("/user/registrationComplete.page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("/user/edit/" + u.getId() + ".page")));

        mvc.perform(formLogin("/user/validateLogin.page").user("newbie").password("secret1")).andExpect(authenticated().withUsername("newbie"));
        verify(mailSender, timeout(3000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        Browser session = acceptedAgreement();
        assertThat(register(session, "Admin", "x@example.com", "a", "a").getResponse().getContentAsString())
            .contains("already in use");
        assertThat(register(session, "other", "admin@localhost", "a", "a").getResponse().getContentAsString())
            .contains("admin@localhost is already in use");
        assertThat(register(session, "", "y@example.com", "a", "a").getResponse().getContentAsString())
            .contains("cannot be empty");
        assertThat(register(session, "x<b>", "y@example.com", "a", "a").getResponse().getContentAsString())
            .doesNotContain("x<b>");
        assertThat(register(session, "mismatch", "m@example.com", "a", "b").getResponse().getContentAsString())
            .contains("confirmation password doesn&#39;t match", "value=\"mismatch\"");
        assertThat(users.findByUsernameIgnoreCase("mismatch")).isEmpty();
    }

    @Test
    void bannedEmailCannotRegister() throws Exception {
        Banlist b = new Banlist();
        b.setId(1);
        b.setEmail("*@spam.example");
        banlist.save(b);
        Browser session = acceptedAgreement();
        register(session, "spammer", "bot@spam.example", "pw", "pw");
        assertThat(users.findByUsernameIgnoreCase("spammer")).isEmpty();
    }

    @Test
    void registrationDisabled() throws Exception {
        config.set("registration.enabled", "false");
        mvc.perform(get("/user/insert.page")).andExpect(content().string(containsString("Registration of new users is disabled")));
    }

    @Test
    void captchaIsCheckedWhenEnabled() throws Exception {
        config.set("captcha.registration", "true");
        Browser session = acceptedAgreement();
        session.perform(get("/captcha/generate/123.page"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"));
        String wrong = session.perform(post("/user/insertSave.page").with(csrf())
                .param("username", "robot").param("email", "robot@example.com").param("password", "pw").param("password_confirm", "pw")
                .param("captchaResponse", "nope"))
            .andReturn().getResponse().getContentAsString();
        assertThat(wrong).contains("captcha_img");
        assertThat(users.findByUsernameIgnoreCase("robot")).isEmpty();

        doReturn("K7PQ2").when(captcha).randomText();
        session.perform(get("/captcha/generate.page"));
        String answer = "K7PQ2";
        session.perform(post("/user/insertSave.page").with(csrf())
                .param("username", "human").param("email", "human@example.com").param("password", "pw").param("password_confirm", "pw")
                .param("captchaResponse", answer))
            .andExpect(redirectedUrl("/user/registrationComplete.page"));
    }

    @Test
    void emailActivationFlow() throws Exception {
        config.set("mail.user.email.auth", "true");
        Browser session = acceptedAgreement();
        String body = register(session, "pending", "pending@example.com", "pw123", "pw123").getResponse().getContentAsString();
        assertThat(body).contains("not activated yet");
        User u = users.findByUsernameIgnoreCase("pending").orElseThrow();
        assertThat(u.isActive()).isFalse();
        assertThat(u.getActivationKey()).isNotBlank();

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(3000)).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("pending@example.com");
        assertThat(mail.getValue().getText()).contains("/user/activateAccount/" + u.getActivationKey() + "/" + u.getId() + ".page");

        Browser loginSession = new Browser();
        mvc.perform(formLogin("/user/validateLogin.page").user("pending").password("pw123")).andExpect(unauthenticated());
        loginSession.perform(post("/user/validateLogin.page").with(csrf()).param("username", "pending").param("password", "pw123"))
            .andExpect(redirectedUrl("/user/login.page?error=1"));
        loginSession.perform(get("/user/login.page").param("error", "1"))
            .andExpect(content().string(containsString("not activated yet")));

        mvc.perform(get("/user/activateAccount/wrong/" + u.getId() + ".page"))
            .andExpect(content().string(containsString("/user/activateManual.page")));
        Browser activation = new Browser();
        activation.perform(get("/user/activateAccount/" + u.getActivationKey() + "/" + u.getId() + ".page"))
            .andExpect(redirectedUrl("/user/registrationComplete.page"));
        activation.perform(get("/forums/list.page")).andExpect(content().string(containsString("[pending]")));
        assertThat(users.findById(u.getId()).orElseThrow().isActive()).isTrue();
        mvc.perform(formLogin("/user/validateLogin.page").user("pending").password("pw123")).andExpect(authenticated());
    }

    @Test
    void lostPasswordFlow() throws Exception {
        mvc.perform(get("/user/lostPassword.page")).andExpect(status().isOk()).andExpect(content().string(containsString("lostPasswordSend")));
        mvc.perform(post("/user/lostPasswordSend.page").with(csrf()).param("username", "nobody"))
            .andExpect(content().string(containsString("Please inform a valid username or email")));
        mvc.perform(post("/user/lostPasswordSend.page").with(csrf()).param("email", "admin@localhost"))
            .andExpect(content().string(containsString("An email was sent")));
        String hash = users.findById(2).orElseThrow().getSecurityHash();
        assertThat(hash).isNotBlank();

        mvc.perform(get("/user/recoverPassword/" + hash + ".page"))
            .andExpect(content().string(containsString("value=\"" + hash + "\"")));
        mvc.perform(post("/user/recoverPasswordValidate.page").with(csrf())
                .param("recoverHash", hash).param("email", "admin@localhost").param("newPassword", "changed").param("password_confirm", "changed"))
            .andExpect(content().string(containsString("Your password was updated")));
        mvc.perform(formLogin("/user/validateLogin.page").user("Admin").password("changed")).andExpect(authenticated());
        mvc.perform(post("/user/recoverPasswordValidate.page").with(csrf())
                .param("recoverHash", hash).param("email", "admin@localhost").param("newPassword", "again"))
            .andExpect(content().string(containsString("invalid")));
    }

    @Test
    void loginUsesLocalRefererAsReturnPath() throws Exception {
        mvc.perform(get("/user/login.page").header("Referer", "http://localhost/posts/list/1.page"))
            .andExpect(content().string(containsString("name=\"returnPath\" value=\"/posts/list/1.page\"")));
        mvc.perform(get("/user/login.page").header("Referer", "http://evil.example/x.page"))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"returnPath\""))));
    }
}
