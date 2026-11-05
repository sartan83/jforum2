package net.jforum.rss;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.xml.parsers.DocumentBuilderFactory;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Topic;
import net.jforum.moderation.Track4Fixtures;
import net.jforum.security.SecurityConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RssIntegrationTest extends Track4Fixtures {

    @Autowired
    ForumConfig config;

    @BeforeEach
    void data() {
        forum(3, "Staff only");
        grant(ADMIN_GROUP, SecurityConstants.PERM_FORUM, "3");
        Topic t = topic(100, 1, "Tom & Jerry <script>", 2, Instant.now());
        addPost(1000, t, 2, "Admin", "Tom & Jerry <script>", "Hello [b]world[/b] & <friends>", false, Instant.now());
        addPost(1001, t, 2, "Admin", "Re: Tom", "second", false, Instant.now().plusSeconds(5));
        Topic hidden = topic(101, 3, "Staff topic", 2, Instant.now());
        addPost(1002, hidden, 2, "Admin", "Staff topic", "secret", false, Instant.now());
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private Document fetch(String url) throws Exception {
        String xml = mvc.perform(get(url)).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/xml"))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Document doc = parse(xml);
        Element rss = doc.getDocumentElement();
        assertThat(rss.getTagName()).isEqualTo("rss");
        assertThat(rss.getAttribute("version")).isEqualTo("2.0");
        Element channel = (Element) rss.getElementsByTagName("channel").item(0);
        assertThat(channel.getElementsByTagName("title").item(0).getTextContent()).isNotBlank();
        assertThat(channel.getElementsByTagName("link").item(0).getTextContent()).startsWith("http");
        assertThat(channel.getElementsByTagName("description").item(0)).isNotNull();
        return doc;
    }

    @Test
    void forumTopicsFeedIsValidRss() throws Exception {
        Document doc = fetch("/rss/forumTopics/1.page");
        var items = doc.getElementsByTagName("item");
        assertThat(items.getLength()).isEqualTo(2);
        Element first = (Element) items.item(0);
        assertThat(first.getElementsByTagName("title").item(0).getTextContent()).isEqualTo("Tom & Jerry <script>");
        String link = first.getElementsByTagName("link").item(0).getTextContent();
        assertThat(link).isEqualTo(config.get(ConfigKeys.FORUM_LINK).replaceAll("/+$", "") + "/" + "posts/preList/100/1000.page");
        assertThat(first.getElementsByTagName("guid").item(0).getTextContent()).isEqualTo(link);
        assertThat(first.getElementsByTagName("pubDate").item(0).getTextContent()).endsWith("GMT");
        assertThat(first.getElementsByTagName("author").item(0).getTextContent()).isEqualTo("Admin");
    }

    @Test
    void topicPostsAndRecentTopicsAndForums() throws Exception {
        assertThat(fetch("/rss/topicPosts/100.page").getElementsByTagName("item").getLength()).isEqualTo(2);
        Document recent = fetch("/rss/recentTopics.page");
        assertThat(recent.getElementsByTagName("item").getLength()).isEqualTo(2);
        assertThat(recent.getDocumentElement().getTextContent()).doesNotContain("Staff topic");
        assertThat(fetch("/rss/forums.page").getDocumentElement().getTextContent()).doesNotContain("Staff only");
        assertThat(fetch("/rss/userTopics/2.page").getElementsByTagName("item").getLength()).isEqualTo(2);
        mvc.perform(get("/jforum.page?module=rss&action=topicPosts&topic_id=100"))
            .andExpect(redirectedUrl("/rss/topicPosts/100.page"));
    }

    @Test
    void forumPermissionsAreEnforced() throws Exception {
        mvc.perform(get("/rss/forumTopics/3.page")).andExpect(status().isForbidden());
        mvc.perform(get("/rss/topicPosts/101.page")).andExpect(status().isForbidden());
        mvc.perform(get("/rss/forumTopics/3.page").with(admin())).andExpect(status().isOk());
    }

    @Test
    void disabledRss() throws Exception {
        config.set(ConfigKeys.RSS_ENABLED, "false");
        mvc.perform(get("/rss/recentTopics.page")).andExpect(status().isNotFound());
        mvc.perform(get("/rss/forumTopics/1.page")).andExpect(status().isNotFound());
    }
}
