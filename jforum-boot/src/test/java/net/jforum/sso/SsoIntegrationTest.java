package net.jforum.sso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jforum.AbstractIntegrationTest;
import net.jforum.config.ForumConfig;
import net.jforum.domain.User;
import net.jforum.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

class SsoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ForumConfig config;

    @Autowired
    private UserRepository users;

    @Test
    void ssoIsOffByDefault() throws Exception {
        mvc.perform(get("/forums/list.page").header("X-Remote-User", "intruder"))
            .andExpect(content().string(not(containsString("intruder"))));
        assertThat(users.findByUsernameIgnoreCase("intruder")).isEmpty();
    }

    @Test
    void remoteUserIsRegisteredAndLoggedIn() throws Exception {
        config.set("authentication.type", "sso");
        config.set("sso.remoteUser.header", "X-Remote-User");
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/forums/list.page").session(session).header("X-Remote-User", "corpuser"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("[corpuser]")));
        User u = users.findByUsernameIgnoreCase("corpuser").orElseThrow();
        assertThat(u.getEmail()).isEqualTo("sso@user");
        assertThat(u.isActive()).isTrue();

        mvc.perform(get("/forums/list.page").session(session).header("X-Remote-User", "corpuser"))
            .andExpect(content().string(containsString("[corpuser]")));
        mvc.perform(get("/forums/list.page").session(session).header("X-Remote-User", "Admin"))
            .andExpect(content().string(containsString("[Admin]")))
            .andExpect(content().string(containsString("/admBase/login.page")));
        mvc.perform(get("/forums/list.page").session(session))
            .andExpect(content().string(not(containsString("[Admin]"))));
    }

    @Test
    void containerRemoteUserAndDisabledLocalFlows() throws Exception {
        config.set("authentication.type", "sso");
        mvc.perform(get("/forums/list.page").with(r -> {
                r.setRemoteUser("container");
                return r;
            }))
            .andExpect(content().string(containsString("[container]")));
        mvc.perform(get("/user/insert.page")).andExpect(content().string(containsString("Registration of new users is disabled")));
        mvc.perform(get("/user/login.page")).andExpect(content().string(containsString("Registration of new users is disabled")));
        config.set("sso.redirect", "https://sso.example/login");
        mvc.perform(get("/user/login.page")).andExpect(redirectedUrl("https://sso.example/login?returnUrl=http://localhost:8080/"));
    }
}
