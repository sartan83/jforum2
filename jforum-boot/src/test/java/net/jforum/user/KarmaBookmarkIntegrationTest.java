package net.jforum.user;

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

import java.time.YearMonth;

import net.jforum.AbstractIntegrationTest;
import net.jforum.domain.Bookmark;
import net.jforum.domain.BookmarkType;
import net.jforum.domain.User;
import net.jforum.repository.BookmarkRepository;
import net.jforum.repository.KarmaRepository;
import net.jforum.repository.PostRepository;
import net.jforum.repository.UserRepository;
import net.jforum.support.SequenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

class KarmaBookmarkIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private KarmaRepository karmas;

    @Autowired
    private PostRepository posts;

    @Autowired
    private BookmarkRepository bookmarks;

    @Autowired
    private SequenceService sequences;

    @Autowired
    private PasswordEncoder encoder;

    private User voter;

    @BeforeEach
    void setUp() {
        voter = UserTestSupport.createUser(users, sequences, encoder, "Voter", "pw");
    }

    @Test
    void karmaVoteOnPost() throws Exception {
        mvc.perform(get("/karma/insert/0/1/4.page").with(as(voter))).andExpect(redirectedUrl("/posts/list/1.page#1"));
        assertThat(karmas.findByPostId(1)).hasSize(1);
        assertThat(posts.findById(1).orElseThrow().getKarma()).isEqualTo(4);
        User admin = users.findById(2).orElseThrow();
        assertThat(admin.getKarmaVotes()).isEqualTo(1);
        assertThat(admin.getKarmaScore()).isEqualTo(4.0);

        mvc.perform(get("/karma/insert/1/3.page").with(as(voter))).andExpect(content().string(containsString("already rated")));
        mvc.perform(get("/karma/insert/0/1/5.page").with(as(admin))).andExpect(content().string(containsString("/posts/list/1.page#1")));
        assertThat(karmas.findByPostId(1)).hasSize(1);
        mvc.perform(get("/karma/insert/0/1/5.page")).andExpect(content().string(containsString("/posts/list/1.page#1")));

        User other = UserTestSupport.createUser(users, sequences, encoder, "Other", "pw");
        mvc.perform(get("/karma/insert/0/1/9.page").with(as(other))).andExpect(content().string(containsString("Invalid karma points")));
        assertThat(karmas.findByPostId(1)).hasSize(1);

        mvc.perform(get("/karma/searchByMonth/" + YearMonth.now() + ".page"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(">Admin<")))
            .andExpect(content().string(containsString(">Voter<")));
    }

    @Test
    void bookmarkLifecycle() throws Exception {
        mvc.perform(get("/bookmarks/insert/2/1.page")).andExpect(content().string(containsString("Anonymous users cannot create bookmarks")));
        mvc.perform(get("/bookmarks/insert/2/1.page").with(as(voter)))
            .andExpect(content().string(containsString("value=\"Welcome to JForum\"")))
            .andExpect(content().string(containsString("/bookmarks/insertSave.page")));
        mvc.perform(post("/bookmarks/insertSave.page").with(csrf()).with(as(voter))
                .param("relation_type", "2").param("relation_id", "1").param("title", "Benvenuto").param("description", "desc"))
            .andExpect(content().string(containsString("The bookmark was saved")));
        Bookmark b = bookmarks.findByUserIdAndRelationTypeAndRelationId(voter.getId(), BookmarkType.TOPIC, 1).orElseThrow();
        assertThat(b.isPublicVisible()).isFalse();

        mvc.perform(get("/bookmarks/list/" + voter.getId() + ".page").with(as(voter)))
            .andExpect(content().string(containsString("Benvenuto")))
            .andExpect(content().string(containsString("/posts/list/1.page")));
        mvc.perform(get("/bookmarks/list/" + voter.getId() + ".page")).andExpect(content().string(not(containsString("Benvenuto"))));

        mvc.perform(post("/bookmarks/updateSave.page").with(csrf()).with(as(voter))
            .param("bookmark_id", String.valueOf(b.getId())).param("title", "Pubblico").param("visible", "1"));
        mvc.perform(get("/bookmarks/list/" + voter.getId() + ".page")).andExpect(content().string(containsString("Pubblico")));

        User admin = users.findById(2).orElseThrow();
        mvc.perform(post("/bookmarks/delete/" + b.getId() + ".page").with(csrf()).with(as(admin)))
            .andExpect(content().string(containsString("does not belong to you")));
        mvc.perform(get("/bookmarks/delete/" + b.getId() + ".page").with(as(voter))).andExpect(content().string(containsString("Really remove")));
        mvc.perform(post("/bookmarks/delete/" + b.getId() + ".page").with(csrf()).with(as(voter)))
            .andExpect(redirectedUrl("/bookmarks/list/" + voter.getId() + ".page"));
        assertThat(bookmarks.count()).isZero();
    }
}
