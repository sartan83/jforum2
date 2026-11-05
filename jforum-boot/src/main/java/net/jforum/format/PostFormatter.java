package net.jforum.format;

import java.util.regex.Matcher;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;

import org.springframework.stereotype.Service;

/**
 * Renders post/PM text to HTML exactly like legacy PostCommon.preparePostForDisplay: escape HTML (unless
 * enabled), newlines to &lt;br /&gt;, SafeHtml, smilies, BBCode (code blocks handled apart), auto links,
 * and a final attribute safety pass.
 */
@Service
public class PostFormatter {

    private final BBCodeRepository bbCodes;
    private final SmilieService smilies;
    private final ForumConfig config;

    public PostFormatter(BBCodeRepository bbCodes, SmilieService smilies, ForumConfig config) {
        this.bbCodes = bbCodes;
        this.smilies = smilies;
        this.config = config;
    }

    public SafeHtml safeHtml() {
        return new SafeHtml(config.get(ConfigKeys.HTML_TAGS_WELCOME), config.get(ConfigKeys.HTML_ATTRIBUTES_WELCOME),
            config.get(ConfigKeys.HTML_LINKS_ALLOW_PROTOCOLS), config.getBool(ConfigKeys.HTML_LINKS_ALLOW_RELATIVE));
    }

    public String format(String raw, boolean htmlEnabled, boolean bbCodeEnabled, boolean smiliesEnabled) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n");
        if (!htmlEnabled) {
            text = text.replace("<", "&lt;").replace(">", "&gt;");
        }
        // The trailing blank space is intentional: some BBCode regexes depend on it
        text = text.replace("\n", "<br /> ");
        SafeHtml safeHtml = safeHtml();
        text = safeHtml.makeSafe(text);
        text = processText(text, bbCodeEnabled, smiliesEnabled);
        return safeHtml.ensureAllAttributesAreSafe(text);
    }

    private String processText(String text, boolean bbCodeEnabled, boolean smiliesEnabled) {
        int codeIndex = text.indexOf("[code");
        int codeEndIndex = codeIndex > -1 ? text.indexOf("[/code]", codeIndex) : -1;
        if (codeIndex == -1 || codeEndIndex == -1 || !bbCodeEnabled) {
            return prepareTextExceptCode(text, bbCodeEnabled, smiliesEnabled);
        }
        StringBuilder result = new StringBuilder(text.length());
        int next = 0;
        while (codeIndex > -1 && codeEndIndex > codeIndex) {
            codeEndIndex += "[/code]".length();
            result.append(prepareTextExceptCode(text.substring(next, codeIndex), true, smiliesEnabled));
            result.append(parseCode(text.substring(codeIndex, codeEndIndex)));
            next = codeEndIndex;
            codeIndex = text.indexOf("[code", codeEndIndex);
            codeEndIndex = codeIndex > -1 ? text.indexOf("[/code]", codeIndex) : -1;
        }
        result.append(prepareTextExceptCode(text.substring(next), true, smiliesEnabled));
        return result.toString();
    }

    String prepareTextExceptCode(String text, boolean bbCodeEnabled, boolean smiliesEnabled) {
        if (smiliesEnabled) {
            text = smilies.process(text);
        }
        if (bbCodeEnabled && text.indexOf('[') > -1 && text.indexOf(']') > -1) {
            for (BBCode bb : bbCodes.getBbList()) {
                if (!bb.tagName().startsWith("code")) {
                    text = bb.regex().matcher(text).replaceAll(bb.replace());
                }
            }
        }
        for (BBCode bb : bbCodes.getAlwaysProcessList()) {
            text = bb.regex().matcher(text).replaceAll(bb.replace());
        }
        return text;
    }

    private String parseCode(String block) {
        for (BBCode bb : bbCodes.getBbList()) {
            if (!bb.tagName().startsWith("code")) {
                continue;
            }
            Matcher m = bb.regex().matcher(block);
            if (m.matches()) {
                boolean plain = "code".equals(bb.tagName());
                String contents = (plain ? m.group(1) : m.group(2)).replace("<br /> ", "\n").replace("<", "&lt;").replace(">", "&gt;");
                String replace = bb.replace();
                if (plain) {
                    return replace.replace("$1", contents);
                }
                String lang = m.group(1).replaceAll("[^A-Za-z0-9_+#-]", "");
                return replace.replace("$1", lang).replace("$2", contents);
            }
        }
        return block;
    }
}
