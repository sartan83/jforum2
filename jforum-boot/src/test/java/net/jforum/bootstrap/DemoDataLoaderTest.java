package net.jforum.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import net.jforum.AbstractIntegrationTest;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.TopicRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.PermissionService;

class DemoDataLoaderTest extends AbstractIntegrationTest {

    @Autowired DemoDataLoader demo;
    @Autowired ForumRepository forums;
    @Autowired TopicRepository topics;
    @Autowired PostRepository posts;
    @Autowired UserRepository users;
    @Autowired PermissionService permissions;

    @Test
    void seedsDemoContentOnceAndMakesItVisible() throws Exception {
        demo.seed();
        demo.seed();
        permissions.invalidate();

        assertThat(forums.count()).isEqualTo(5);
        assertThat(topics.count()).isEqualTo(9);
        assertThat(posts.count()).isEqualTo(25);
        assertThat(users.findByUsernameIgnoreCase("mario")).isPresent();

        mvc.perform(get("/forums/list.page"))
            .andExpect(content().string(containsString("Java &amp; Spring Boot")))
            .andExpect(content().string(containsString("MongoDB")));
    }
}
