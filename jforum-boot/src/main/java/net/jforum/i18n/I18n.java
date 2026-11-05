package net.jforum.i18n;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Template helper exposed as ${I18n} so ported templates keep the legacy syntax
 * ${I18n.getMessage("ForumListing.forum")}. Bundles are the legacy WEB-INF/config/languages files.
 */
@Component("I18n")
public class I18n {

    private final MessageSource messages;

    public I18n(MessageSource messages) {
        this.messages = messages;
    }

    public String getMessage(String key) {
        return messages.getMessage(key, null, key, locale());
    }

    public String getMessage(String key, Object... args) {
        return messages.getMessage(key, args, key, locale());
    }

    public String getMessage(String key, Locale locale) {
        return messages.getMessage(key, null, key, locale);
    }

    private static Locale locale() {
        return LocaleContextHolder.getLocale();
    }
}
