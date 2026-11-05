package net.jforum.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Smilie;
import net.jforum.repository.SmilieRepository;

/** BBCode / smilies / HTML escaping, as in legacy PostCommon.preparePostForDisplay. */
class PostFormatterTest {

    private PostFormatter formatter;

    @BeforeEach
    void setUp() {
        ForumConfig config = Mockito.mock(ForumConfig.class);
        Mockito.when(config.get(ConfigKeys.HTML_TAGS_WELCOME)).thenReturn("u, a, img, i, u, li, ul, font, br, p, b, hr");
        Mockito.when(config.get(ConfigKeys.HTML_ATTRIBUTES_WELCOME)).thenReturn("src, href, size, face, color, target, rel");
        Mockito.when(config.get(ConfigKeys.HTML_LINKS_ALLOW_PROTOCOLS)).thenReturn("http://, https://, mailto:, ftp://");
        SmilieRepository repo = Mockito.mock(SmilieRepository.class);
        Smilie smile = new Smilie();
        smile.setCode(":)");
        smile.setDiskName("smile.gif");
        Mockito.when(repo.findAll()).thenReturn(List.of(smile));
        formatter = new PostFormatter(new BBCodeRepository(), new SmilieService(repo), config);
    }

    @Test
    void basicBBCode() {
        String html = formatter.format("[b]bold[/b] [i]it[/i] [u]u[/u]", false, true, false);
        assertThat(html).contains("<b>bold</b>").contains("<i>it</i>").contains("<u>u</u>");
    }

    @Test
    void quoteWithAuthor() {
        String html = formatter.format("[quote=Admin]hello[/quote]", false, true, false);
        assertThat(html).contains("<blockquote>").contains("Admin wrote:").contains("hello");
    }

    @Test
    void urlAndImg() {
        String html = formatter.format("[url=http://jforum.net]JForum[/url] [img]http://x/y.png[/img]", false, true, false);
        assertThat(html).contains("href=\"http://jforum.net\"").contains("src=\"http://x/y.png\"");
    }

    @Test
    void htmlIsEscapedWhenDisabled() {
        String html = formatter.format("<script>alert(1)</script>", false, true, true);
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void htmlEnabledStillStripsDangerousMarkup() {
        String html = formatter.format("<b onclick='x()'>ok</b><script>alert(1)</script>", true, true, true);
        assertThat(html).contains("<b >ok</b>").doesNotContain("<script>");
    }

    @Test
    void javascriptUrlInBBCodeIsRemoved() {
        String html = formatter.format("[url=javascript:alert(1)]x[/url]", false, true, false);
        assertThat(html).doesNotContain("javascript:");
    }

    @Test
    void codeBlockIsNotParsedForBBCodeOrSmilies() {
        String html = formatter.format("before :) [code][b]x[/b] <tag> :)[/code] after", false, true, true);
        assertThat(html).contains("<pre class=\"bbcode-code\"><code>[b]x[/b] &lt;tag&gt; :)</code></pre>")
            .contains("before <img src=\"/images/smilies/smile.gif\"");
    }

    @Test
    void newlinesBecomeBr() {
        assertThat(formatter.format("a\nb", false, false, false)).isEqualTo("a<br /> b");
    }

    @Test
    void autoUrl() {
        assertThat(formatter.format("see http://jforum.net now", false, true, false)).contains("href=\"http://jforum.net\"");
    }
}
