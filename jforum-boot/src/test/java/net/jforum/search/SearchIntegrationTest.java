package net.jforum.search;

import java.time.Instant;

import net.jforum.domain.Topic;
import net.jforum.moderation.Track4Fixtures;
import net.jforum.security.SecurityConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchIntegrationTest extends Track4Fixtures {

    @BeforeEach
    void data() {
        Instant now = Instant.parse("2026-03-10T10:00:00Z");
        forum(2, "Public");
        forum(3, "Staff only");
        grant(GENERAL, SecurityConstants.PERM_FORUM, "2");
        grant(ADMIN_GROUP, SecurityConstants.PERM_FORUM, "2", "3");
        member(10, "bob", GENERAL);
        Topic a = topic(100, 2, "Kubernetes deployment guide", 10, now);
        addPost(1000, a, 10, "bob", "Kubernetes deployment guide", "How to deploy jforum with helm charts", false, now);
        Topic b = topic(101, 3, "Secret roadmap", 2, now.plusSeconds(10));
        addPost(1001, b, 2, "Admin", "Secret roadmap", "helm charts upgrade plan for staff", false, now.plusSeconds(10));
        Topic c = topic(102, 2, "Pending", 10, now.plusSeconds(20));
        addPost(1002, c, 10, "bob", "Pending", "helm charts waiting moderation", true, now.plusSeconds(20));
        Topic d = topic(103, 2, "Zebra stripes", 10, Instant.parse("2025-01-01T00:00:00Z"));
        addPost(1003, d, 10, "bob", "Animals", "old helm message", false, Instant.parse("2025-01-01T00:00:00Z"));
    }

    @Test
    void textIndexesExistAtStartup() {
        assertThat(mongo.getCollection("posts").listIndexes().into(new java.util.ArrayList<>()))
            .anyMatch(i -> i.get("key", org.bson.Document.class).containsKey("_fts"));
        assertThat(mongo.getCollection("topics").listIndexes().into(new java.util.ArrayList<>()))
            .anyMatch(i -> i.get("key", org.bson.Document.class).containsKey("_fts"));
    }

    @Test
    void filtersPageRendersForm() throws Exception {
        mvc.perform(get("/search/filters.page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"search_keywords\"")));
    }

    @Test
    void resultsRespectForumReadPermissions() throws Exception {
        mvc.perform(get("/search/search.page").param("search_keywords", "helm charts").with(as(10, "bob")))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalRecords", 1L))
            .andExpect(content().string(containsString("Kubernetes deployment guide")))
            .andExpect(content().string(not(containsString("Secret roadmap"))))
            .andExpect(content().string(not(containsString("waiting moderation"))));

        mvc.perform(get("/search/search.page").param("search_keywords", "helm charts").with(admin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalRecords", 2L))
            .andExpect(content().string(containsString("Secret roadmap")));

        // forum 3 explicitly requested by a user that cannot read it: still nothing
        mvc.perform(get("/search/search.page").param("search_keywords", "roadmap").param("search_forum", "3").with(as(10, "bob")))
            .andExpect(model().attribute("totalRecords", 0L));
    }

    @Test
    void matchTypeAuthorDateAndTitleFilters() throws Exception {
        mvc.perform(get("/search/search.page").param("search_keywords", "helm kubernetes").param("match_type", "any").with(admin()))
            .andExpect(model().attribute("totalRecords", 3L));
        mvc.perform(get("/search/search.page").param("search_keywords", "helm kubernetes").param("match_type", "all").with(admin()))
            .andExpect(model().attribute("totalRecords", 1L));
        mvc.perform(get("/search/search.page").param("search_keywords", "helm").param("search_author", "Admin").with(admin()))
            .andExpect(model().attribute("totalRecords", 1L));
        mvc.perform(get("/search/search.page").param("search_keywords", "helm").param("from_date", "2026-01-01")
                .param("sort_by", "time").param("sort_dir", "ASC").with(admin()))
            .andExpect(model().attribute("totalRecords", 2L));
        // topic title index: "zebra" only appears in the topic title
        mvc.perform(get("/search/search.page").param("search_keywords", "zebra").with(admin()))
            .andExpect(model().attribute("totalRecords", 1L))
            .andExpect(content().string(containsString("old helm message")));
    }

    @Test
    void paginationUsesStartOffset() throws Exception {
        mongo.updateMulti(new org.springframework.data.mongodb.core.query.Query(),
            org.springframework.data.mongodb.core.query.Update.update("value", "1"), "config");
        mvc.perform(get("/search/search.page").param("search_keywords", "helm").param("match_type", "any").with(admin()))
            .andExpect(status().isOk());
        mvc.perform(get("/search/search.page").param("search_keywords", "helm").param("start", "15").with(admin()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("No results")));
    }

    @Test
    void newMessages() throws Exception {
        mvc.perform(get("/search/newMessages.page").param("from_date", "2026-01-01").with(as(10, "bob")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Kubernetes deployment guide")))
            .andExpect(content().string(not(containsString("Secret roadmap"))))
            .andExpect(content().string(not(containsString("Zebra stripes"))));
    }
}
