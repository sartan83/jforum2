package net.jforum.user;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import net.jforum.config.ForumConfig;
import net.jforum.i18n.I18n;
import net.jforum.security.CurrentUser;
import net.jforum.sso.SsoSettings;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** GET /user/login.page; POST /user/validateLogin.page and /user/logout.page are handled by Spring Security. */
@Controller
public class LoginController {

    private final CurrentUser currentUser;
    private final ForumConfig config;
    private final SsoSettings sso;
    private final I18n i18n;

    public LoginController(CurrentUser currentUser, ForumConfig config, SsoSettings sso, I18n i18n) {
        this.currentUser = currentUser;
        this.config = config;
        this.sso = sso;
        this.i18n = i18n;
    }

    @GetMapping("/user/login.page")
    public String login(@RequestParam(required = false) String error, @RequestParam(required = false) String returnPath,
                        HttpServletRequest request, Model model) {
        if (sso.isEnabled()) {
            if (!sso.redirect().isBlank()) {
                return "redirect:" + sso.redirect() + "?returnUrl=" + sso.forumLink();
            }
            model.addAttribute("messageTitle", i18n.getMessage("ForumBase.login"));
            model.addAttribute("messageHtml", i18n.getMessage("User.registrationDisabled"));
            return "user_message";
        }
        if (currentUser.isLogged()) {
            return "redirect:/forums/list.page";
        }
        model.addAttribute("invalidLogin", error != null);
        if (error != null) {
            HttpSession session = request.getSession(false);
            Object ex = session == null ? null : session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            if (ex instanceof DisabledException) {
                model.addAttribute("loginError", i18n.getMessage("Login.accountNotActivated"));
            }
        }
        if (returnPath == null && !config.getBool("login.ignore.referer")) {
            returnPath = localReferer(request);
        }
        model.addAttribute("returnPath", returnPath);
        return "login";
    }

    /** Legacy login(): the Referer becomes the returnPath, restricted to same-host local paths. */
    private static String localReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return null;
            }
            String path = uri.getRawPath();
            if (path == null || !path.startsWith(request.getContextPath() + "/") || path.contains("/user/login")
                || path.contains("/user/insert") || path.contains("/user/validateLogin")) {
                return null;
            }
            path = path.substring(request.getContextPath().length());
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
