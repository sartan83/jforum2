package net.jforum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** JUnit 5 port of legacy tests/core/net/jforum/util/SafeHtmlTest. */
class SafeHtmlTest {

    private static final String WELCOME_TAGS = "a, b, i, u, img";
    private static final String ATTRIBUTES = "src, href, size, face, color, target, rel";
    private static final String PROTOCOLS = "http://, https://, mailto:, ftp://";

    private final SafeHtml safeHtml = new SafeHtml(WELCOME_TAGS, ATTRIBUTES, PROTOCOLS, false);

    @Test
    void makeSafe() {
        String input = "<a href='http://somelink'>Some Link</a>"
            + "bla <b>bla</b> <pre>code code</pre>"
            + "<script>document.location = 'xxx';</script>"
            + "<img src='http://imgPath' onLoad='window.close();'>"
            + "<a href='javascript:alert(bleh)'>xxxx</a>"
            + "<img src='javascript:alert(bloh)'>"
            + "<img src=\"&#106ava&#115cript&#58aler&#116&#40&#39Oops&#39&#41&#59\">"
            + "\"> TTTTT <"
            + "<img src='http://some.image' onLoad=\"javascript:alert('boo')\">"
            + "<b>heeelooo, nurse</b>"
            + "<b style='some style'>1, 2, 3</b>";
        String expected = "<a href='http://somelink'>Some Link</a>"
            + "bla <b>bla</b> &lt;pre&gt;code code&lt;/pre&gt;"
            + "&lt;script&gt;document.location = 'xxx';&lt;/script&gt;"
            + "<img src='http://imgPath' >"
            + "<a >xxxx</a>"
            + "<img >"
            + "<img >"
            + "&quot;&gt; TTTTT &lt;"
            + "<img src='http://some.image' >"
            + "<b>heeelooo, nurse</b>"
            + "<b >1, 2, 3</b>";
        assertEquals(expected, safeHtml.makeSafe(input));
    }

    @Test
    void javascriptInsideUrlTagIsRemoved() {
        String input = "<a class=\"snap_shots\" rel=\"nofollow\" target=\"_new\" onmouseover=\"javascript:alert('test2');\" href=\"before\">test</a>";
        String expected = "<a class=\"snap_shots\" rel=\"nofollow\" target=\"_new\"  >test</a>";
        assertEquals(expected, safeHtml.ensureAllAttributesAreSafe(input));
    }

    @Test
    void javascriptInsideImageTagIsRemoved() {
        String input = "<img border=\"0\" onmouseover=\"javascript:alert('buuuh!!!');\"\"\" src=\"javascript:alert('hi from an alert!');\"/>";
        String expected = "<img border=\"0\" \"\" />";
        assertEquals(expected, safeHtml.ensureAllAttributesAreSafe(input));
    }

    @Test
    void iframeIsEscaped() {
        String input = "<iframe src='http://www.google.com' onload='javascript:parent.document.body.style.display='none'; alert('where is the forum?'); ' style='display:none;'></iframe>";
        String output = "&lt;iframe src='http://www.google.com' onload='javascript:parent.document.body.style.display='none'; alert('where is the forum?'); ' style='display:none;'&gt;&lt;/iframe&gt;";
        assertEquals(output, safeHtml.makeSafe(input));
    }

    @Test
    void relativeLinksOnlyWhenAllowed() {
        assertEquals("<a >x</a>", safeHtml.makeSafe("<a href='/local'>x</a>"));
        SafeHtml relative = new SafeHtml(WELCOME_TAGS, ATTRIBUTES, PROTOCOLS, true);
        assertEquals("<a href='/local'>x</a>", relative.makeSafe("<a href='/local'>x</a>"));
        assertEquals("<a >x</a>", relative.makeSafe("<a href='//evil.com'>x</a>"));
    }
}
