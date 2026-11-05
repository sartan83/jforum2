package net.jforum.web;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.i18n.I18n;
import net.jforum.security.CurrentUser;
import net.jforum.security.SecurityConstants;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Variables every legacy template expected in its context (see legacy JForum.prepareTemplateContext):
 * forumName, pageTitle, contextPath, logged, session user, permission helper, I18n.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final ForumConfig config;
    private final CurrentUser currentUser;
    private final I18n i18n;

    public GlobalModelAdvice(ForumConfig config, CurrentUser currentUser, I18n i18n) {
        this.config = config;
        this.currentUser = currentUser;
        this.i18n = i18n;
    }

    @ModelAttribute
    public void common(Model model, HttpServletRequest request) {
        model.addAttribute("I18n", i18n);
        model.addAttribute("forumName", config.forumName());
        model.addAttribute("pageTitle", config.get(ConfigKeys.FORUM_PAGE_TITLE));
        model.addAttribute("metaKeywords", config.get(ConfigKeys.PAGE_METATAG_KEYWORDS));
        model.addAttribute("metaDescription", config.get(ConfigKeys.PAGE_METATAG_DESCRIPTION));
        model.addAttribute("contextPath", request.getContextPath());
        model.addAttribute("currentUser", currentUser.get());
        model.addAttribute("logged", currentUser.isLogged());
        model.addAttribute("isAdmin", currentUser.isAdministrator());
        model.addAttribute("canModerate", currentUser.canAccess(SecurityConstants.PERM_MODERATION));
        model.addAttribute("perm", currentUser.permissions());
        model.addAttribute("registrationEnabled", config.getBool(ConfigKeys.REGISTRATION_ENABLED));
        model.addAttribute("rssEnabled", config.getBool(ConfigKeys.RSS_ENABLED));
        model.addAttribute("requestUri", request.getRequestURI().substring(request.getContextPath().length()));
    }
}
