package net.jforum.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import net.jforum.AbstractIntegrationTest;

class FoundationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void rootRedirectsToForumIndex() throws Exception {
        mvc.perform(get("/")).andExpect(redirectedUrl("/forums/list.page"));
    }

    @Test
    void forumIndexShowsBootstrapForumToAnonymous() throws Exception {
        mvc.perform(get("/forums/list.page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Category Test")))
            .andExpect(content().string(containsString("Test Forum")))
            .andExpect(content().string(not(containsString("Admin Control Panel"))));
    }

    @Test
    void legacyFrontControllerUrlRedirectsToFriendlyUrl() throws Exception {
        mvc.perform(get("/jforum.page").param("module", "posts").param("action", "list").param("topic_id", "1"))
            .andExpect(redirectedUrl("/posts/list/1.page"));
        mvc.perform(get("/jforum.page").param("module", "forums").param("action", "show").param("forum_id", "3").param("start", "15"))
            .andExpect(redirectedUrl("/forums/show/15/3.page"));
        mvc.perform(get("/jforum.page")).andExpect(redirectedUrl("/forums/list.page"));
    }

    @Test
    void adminLoginWithBootstrapPassword() throws Exception {
        mvc.perform(formLogin("/user/validateLogin.page").user("Admin").password("admin"))
            .andExpect(authenticated().withUsername("Admin"))
            .andExpect(redirectedUrl("/forums/list.page"));
        mvc.perform(formLogin("/user/validateLogin.page").user("Admin").password("wrong"))
            .andExpect(unauthenticated())
            .andExpect(redirectedUrl("/user/login.page?error=1"));
    }

    @Test
    void anonymousCannotLogIn() throws Exception {
        mvc.perform(formLogin("/user/validateLogin.page").user("Anonymous").password("nopass")).andExpect(unauthenticated());
    }

    @Test
    void loginPageRenders() throws Exception {
        mvc.perform(get("/user/login.page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"username\"")))
            .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    @WithUserDetails("Admin")
    void adminSeesAdminLink() throws Exception {
        mvc.perform(get("/forums/list.page")).andExpect(content().string(containsString("/admBase/login.page")));
    }

    @Test
    void adminAreaRequiresAdministrator() throws Exception {
        mvc.perform(get("/adminForums/list.page")).andExpect(status().is3xxRedirection());
    }

    @Test
    void logoutRequiresPost() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/user/logout.page").with(csrf()))
            .andExpect(redirectedUrl("/forums/list.page"));
    }
}
