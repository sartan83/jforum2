package net.jforum.format;

import java.util.regex.Pattern;

/** One &lt;match&gt; entry of bb_config.xml. */
public record BBCode(String tagName, Pattern regex, String replace, boolean alwaysProcess) {
}
