package net.jforum.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.jforum.AbstractIntegrationTest;
import net.jforum.domain.ApiKey;
import net.jforum.repository.ApiKeyRepository;
import net.jforum.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRestIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void keys() {
        ApiKey valid = new ApiKey();
        valid.setId(1);
        valid.setApiKey("valid-key");
        valid.setValidity(Instant.now().plus(1, ChronoUnit.DAYS));
        ApiKey expired = new ApiKey();
        expired.setId(2);
        expired.setApiKey("expired-key");
        expired.setValidity(Instant.now().minus(1, ChronoUnit.DAYS));
        apiKeys.save(valid);
        apiKeys.save(expired);
    }

    @Test
    void requestsWithoutValidKeyAreRejected() throws Exception {
        mvc.perform(get("/userApi/list.page"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/xml"))
            .andExpect(content().string(containsString("<jforum-api response-code=\"ERROR\">")))
            .andExpect(content().string(containsString("The parameter 'api_key' was not found")));
        mvc.perform(get("/userApi/list.page").param("api_key", "wrong"))
            .andExpect(content().string(containsString("The provided API authentication information is not valid")));
        mvc.perform(get("/userApi/list.page").param("api_key", "expired-key"))
            .andExpect(content().string(containsString("response-code=\"ERROR\"")));
        mvc.perform(post("/userApi/insert.page").param("api_key", "wrong").param("username", "x").param("email", "x@x").param("password", "p"))
            .andExpect(content().string(containsString("response-code=\"ERROR\"")));
        assertThat(users.findByUsernameIgnoreCase("x")).isEmpty();
    }

    @Test
    void listUsersAsLegacyXml() throws Exception {
        mvc.perform(get("/userApi/list.page").param("api_key", "valid-key"))
            .andExpect(content().string(containsString("<jforum-api response-code=\"OK\">")))
            .andExpect(content().string(containsString("<users total=\"2\">")))
            .andExpect(content().string(containsString("<user id=\"2\" username=\"Admin\" email=\"admin@localhost\"/>")));
    }

    @Test
    void insertUserWithoutCsrfAndLogin() throws Exception {
        mvc.perform(post("/userApi/insert.page").param("api_key", "valid-key")
                .param("username", "apiuser").param("email", "api@example.com").param("password", "apipw"))
            .andExpect(content().string(containsString("<jforum-api response-code=\"OK\">")))
            .andExpect(content().string(containsString("<user id=\"3\"/>")));
        mvc.perform(formLogin("/user/validateLogin.page").user("apiuser").password("apipw")).andExpect(authenticated());

        mvc.perform(post("/userApi/insert.page").param("api_key", "valid-key")
                .param("username", "apiuser").param("email", "other@example.com").param("password", "apipw"))
            .andExpect(content().string(containsString("response-code=\"ERROR\"")));
        mvc.perform(post("/userApi/insert.page").param("api_key", "valid-key").param("username", "noemail").param("password", "p"))
            .andExpect(content().string(containsString("The parameter 'email' was not found")));
    }

    @Test
    void legacyMd5PasswordIsStoredAsHash() throws Exception {
        mvc.perform(post("/userApi/insert.page").param("api_key", "valid-key")
                .param("username", "md5user").param("email", "md5@example.com").param("password", "5f4dcc3b5aa765d61d8327deb882cf99"))
            .andExpect(content().string(containsString("response-code=\"OK\"")));
        mvc.perform(formLogin("/user/validateLogin.page").user("md5user").password("password")).andExpect(authenticated());
    }

    @Test
    void jsonResponses() throws Exception {
        mvc.perform(get("/userApi/list.page").param("api_key", "valid-key").param("format", "json"))
            .andExpect(jsonPath("$['response-code']").value("OK"))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.users[1].username").value("Admin"));
        mvc.perform(get("/userApi/list.page").header("Accept", "application/json"))
            .andExpect(jsonPath("$['response-code']").value("ERROR"));
        mvc.perform(post("/userApi/insert.page").param("api_key", "valid-key").param("format", "json")
                .param("username", "jsonuser").param("email", "j@example.com").param("password", "pw"))
            .andExpect(jsonPath("$.user.id").value(3));
    }
}
