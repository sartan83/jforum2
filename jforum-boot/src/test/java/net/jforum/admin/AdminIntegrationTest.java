package net.jforum.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import net.jforum.AbstractIntegrationTest;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Category;
import net.jforum.domain.ConfigEntry;
import net.jforum.domain.Forum;
import net.jforum.domain.Group;
import net.jforum.domain.Post;
import net.jforum.domain.Role;
import net.jforum.domain.User;
import net.jforum.repository.CategoryRepository;
import net.jforum.repository.ForumRepository;
import net.jforum.repository.GroupRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.UserRepository;
import net.jforum.security.SecurityConstants;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminIntegrationTest extends AbstractIntegrationTest {

    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired CategoryRepository categories;
    @Autowired ForumRepository forums;
    @Autowired PostRepository posts;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserDetailsService userDetailsService;
    @Autowired ForumConfig config;

    private User createUser(String username) {
        User u = new User();
        u.setId(100 + (int) users.count());
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setPassword(passwordEncoder.encode("secret"));
        u.setRegistrationDate(Instant.now());
        u.setGroupIds(new ArrayList<>(List.of(1)));
        return users.save(u);
    }

    private MockHttpServletRequestBuilder as(String username, MockHttpServletRequestBuilder request) {
        return request.with(user(userDetailsService.loadUserByUsername(username)));
    }

    @Test
    void adminPagesRequireAdministrator() throws Exception {
        mvc.perform(get("/admBase/login.page")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/adminCategories/list.page")).andExpect(status().is3xxRedirection());
        createUser("bob");
        mvc.perform(as("bob", get("/admBase/login.page"))).andExpect(status().isForbidden());
        mvc.perform(as("bob", get("/adminUsers/list.page"))).andExpect(status().isForbidden());
        mvc.perform(as("bob", get("/admBase/main.page"))).andExpect(status().isForbidden())
            .andExpect(forwardedUrl("/forbidden.page"));
        mvc.perform(as("bob", get("/forbidden.page"))).andExpect(status().isForbidden())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("message-card")));
        mvc.perform(as("bob", post("/adminConfig/editSave.page").with(csrf()).param("p_forum.name", "hacked")))
            .andExpect(status().isForbidden());
        assertThat(config.forumName()).isNotEqualTo("hacked");
    }

    @Test
    @WithUserDetails("Admin")
    void adminHomeRendersMenuAndAllListPages() throws Exception {
        mvc.perform(get("/admBase/login.page")).andExpect(status().isOk())
            .andExpect(content().string(containsString("admin-layout")))
            .andExpect(content().string(containsString("/adminCategories/list.page")))
            .andExpect(content().string(containsString("/adminModeration/list.page")));
        for (String url : List.of("/admBase/menu.page", "/adminCategories/list.page", "/adminCategories/insert.page",
            "/adminForums/list.page", "/adminForums/insert.page", "/adminForums/edit/1.page", "/adminGroups/list.page",
            "/adminGroups/insert.page", "/adminGroups/edit/1.page", "/adminGroups/permissions/1.page",
            "/adminUsers/list.page", "/adminUsers/edit/2.page", "/adminUsers/groups/2.page",
            "/adminUsers/pendingActivations.page", "/adminRankings/list.page", "/adminRankings/insert.page",
            "/adminSmilies/list.page", "/adminSmilies/insert.page", "/adminConfig/list.page", "/adminBanning/list.page",
            "/adminBanning/insert.page", "/adminAttachments/configurations.page", "/adminAttachments/quotaLimit.page",
            "/adminAttachments/extensionGroups.page", "/adminAttachments/extensions.page", "/adminModeration/list.page",
            "/adminCache/list.page", "/adminSearchStats/list.page", "/adminBanner/list.page", "/adminBanner/insert.page",
            "/adminCategories/edit/1.page")) {
            mvc.perform(get(url)).andExpect(status().isOk());
        }
    }

    @Test
    @WithUserDetails("Admin")
    void createCategoryAndForumShowsOnForumIndex() throws Exception {
        mvc.perform(post("/adminCategories/insertSave.page").with(csrf())
                .param("category_name", "Admin Category").param("moderated", "0").param("groups", "1", "2"))
            .andExpect(redirectedUrl("/adminCategories/list.page"));
        Category category = categories.findAll().stream().filter(c -> c.getTitle().equals("Admin Category")).findFirst().orElseThrow();

        mvc.perform(post("/adminForums/insertSave.page").with(csrf())
                .param("forum_name", "Admin Forum").param("description", "created by admin")
                .param("categories_id", Integer.toString(category.getId())).param("moderate", "0")
                .param("groups", "1", "2"))
            .andExpect(redirectedUrl("/adminForums/list.page"));
        Forum forum = forums.findAll().stream().filter(f -> f.getName().equals("Admin Forum")).findFirst().orElseThrow();
        assertThat(forum.getCategoryId()).isEqualTo(category.getId());
        Group general = groups.findById(1).orElseThrow();
        assertThat(roleValues(general, SecurityConstants.PERM_CATEGORY)).contains(category.getId().toString());
        assertThat(roleValues(general, SecurityConstants.PERM_FORUM)).contains(forum.getId().toString());

        mvc.perform(get("/forums/list.page")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Admin Category")))
            .andExpect(content().string(containsString("Admin Forum")));

        // ordering: the new category moves above the bootstrap one
        mvc.perform(get("/adminCategories/up/" + category.getId() + ".page")).andExpect(status().is3xxRedirection());
        assertThat(categories.findAllByOrderByOrderAsc().get(0).getId()).isEqualTo(category.getId());
    }

    @Test
    @WithUserDetails("Admin")
    void permissionEditorRemovesForumAccess() throws Exception {
        Forum forum = forums.findById(1).orElseThrow();
        Group general = groups.findById(1).orElseThrow();
        assertThat(roleValues(general, SecurityConstants.PERM_FORUM)).contains("1");
        mvc.perform(get("/forums/list.page").with(user(userDetailsService.loadUserByUsername(createUser("carol").getUsername()))))
            .andExpect(content().string(containsString(forum.getName())));

        mvc.perform(get("/adminGroups/permissions/1.page")).andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"perm_forum\"")))
            .andExpect(content().string(containsString("name=\"perm_administration$single\"")));

        // keep the category, restrict (select) forum 1 in "perm_forum"
        mvc.perform(post("/adminGroups/permissionsSave.page").with(csrf()).param("id", "1")
                .param("perm_category", "all").param("allperm_category", ";1")
                .param("perm_forum", "1").param("allperm_forum", ";1")
                .param("perm_administration$single", "deny"))
            .andExpect(redirectedUrl("/adminGroups/list.page"));

        general = groups.findById(1).orElseThrow();
        assertThat(roleValues(general, SecurityConstants.PERM_CATEGORY)).containsExactly("1");
        assertThat(roleValues(general, SecurityConstants.PERM_FORUM)).isEmpty();
        assertThat(general.getRoles()).noneMatch(r -> r.getName().equals(SecurityConstants.PERM_ADMINISTRATION));

        mvc.perform(get("/forums/list.page").with(user(userDetailsService.loadUserByUsername("carol"))))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/forums/show/1.page"))));
        // Admin (also member of Administration) still sees it
        mvc.perform(get("/forums/list.page")).andExpect(content().string(containsString("/forums/show/1.page")));
    }

    @Test
    @WithUserDetails("Admin")
    void editUserAndAssignGroups() throws Exception {
        User dave = createUser("dave");
        mvc.perform(post("/adminUsers/editSave.page").with(csrf()).param("user_id", dave.getId().toString())
                .param("username", "dave2").param("email", "dave2@example.com").param("location", "Milano")
                .param("signature", "ciao").param("viewemail", "1").param("new_password", "newpass"))
            .andExpect(redirectedUrl("/adminUsers/list.page"));
        User saved = users.findById(dave.getId()).orElseThrow();
        assertThat(saved.getUsername()).isEqualTo("dave2");
        assertThat(saved.getEmail()).isEqualTo("dave2@example.com");
        assertThat(saved.getFrom()).isEqualTo("Milano");
        assertThat(saved.isViewEmail()).isTrue();
        assertThat(passwordEncoder.matches("newpass", saved.getPassword())).isTrue();

        mvc.perform(post("/adminUsers/groupsSave.page").with(csrf()).param("user_id", dave.getId().toString())
            .param("groups", "1", "2")).andExpect(status().is3xxRedirection());
        assertThat(users.findById(dave.getId()).orElseThrow().getGroupIds()).containsExactly(1, 2);

        mvc.perform(get("/adminUsers/search.page").param("username", "dave"))
            .andExpect(content().string(containsString("dave2@example.com")));

        mvc.perform(post("/adminUsers/delete.page").with(csrf()).param("user_id", dave.getId().toString()))
            .andExpect(status().is3xxRedirection());
        assertThat(users.findById(dave.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @WithUserDetails("Admin")
    void configOverrideIsPersisted() throws Exception {
        mvc.perform(post("/adminConfig/editSave.page").with(csrf())
                .param("p_forum.name", "Forum Admin Test").param("p_topicsPerPage", "7"))
            .andExpect(redirectedUrl("/adminConfig/list.page"));
        assertThat(mongo.findById("forum.name", ConfigEntry.class).getValue()).isEqualTo("Forum Admin Test");
        assertThat(config.topicsPerPage()).isEqualTo(7);
        mvc.perform(get("/forums/list.page")).andExpect(content().string(containsString("Forum Admin Test")));
        mvc.perform(post("/adminCache/clear.page").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(config.forumName()).isEqualTo("Forum Admin Test");
    }

    @Test
    @WithUserDetails("Admin")
    void moderationQueueApprovesAndRejects() throws Exception {
        Post first = posts.findById(1).orElseThrow();
        int forumPosts = forums.findById(first.getForumId()).orElseThrow().getTotalPosts();
        Post pending = pendingPost(9001, first);
        Post rejected = pendingPost(9002, first);

        mvc.perform(get("/adminModeration/list.page")).andExpect(content().string(containsString("/adminModeration/view/" + first.getForumId() + ".page")));
        mvc.perform(get("/adminModeration/view/" + first.getForumId() + ".page")).andExpect(content().string(containsString("status_9001")));
        mvc.perform(post("/adminModeration/save.page").with(csrf()).param("forum_id", first.getForumId().toString())
                .param("post_id", "9001", "9002").param("status_9001", "aprove").param("status_9002", "reject"))
            .andExpect(status().is3xxRedirection());

        assertThat(posts.findById(pending.getId()).orElseThrow().isModerate()).isFalse();
        assertThat(posts.findById(rejected.getId())).isEmpty();
        assertThat(forums.findById(first.getForumId()).orElseThrow().getTotalPosts()).isEqualTo(forumPosts + 1);
    }

    @Test
    @WithUserDetails("Admin")
    void rankingsBanlistAndExtensionsCrud() throws Exception {
        mvc.perform(post("/adminRankings/insertSave.page").with(csrf()).param("rank_title", "Guru").param("rank_min", "500"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/adminRankings/list.page")).andExpect(content().string(containsString("Guru")));
        mvc.perform(post("/adminBanning/insertSave.page").with(csrf()).param("type", "email").param("value", "spam@example.com"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/adminBanning/list.page")).andExpect(content().string(containsString("spam@example.com")));
        mvc.perform(post("/adminAttachments/extensionsSave.page").with(csrf()).param("extension", ".ZIP").param("allow", "1"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/adminAttachments/extensions.page")).andExpect(content().string(containsString("<code>zip</code>")));
    }

    private Post pendingPost(int id, Post template) {
        Post p = new Post();
        p.setId(id);
        p.setTopicId(template.getTopicId());
        p.setForumId(template.getForumId());
        p.setUserId(2);
        p.setUsername("Admin");
        p.setTime(Instant.now());
        p.setSubject("pending " + id);
        p.setText("pending text " + id);
        p.setModerate(true);
        return posts.save(p);
    }

    private static List<String> roleValues(Group g, String name) {
        return g.getRoles().stream().filter(r -> r.getName().equals(name)).findFirst().map(Role::getValues).orElse(List.of());
    }
}
