package net.jforum.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Styled page for requests denied by Spring Security (e.g. non-admins on /adm* modules). */
@Controller
public class ForbiddenController {

    public static final String PATH = "/forbidden.page";

    @RequestMapping(PATH)
    public String forbidden(Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("messageKey", "Moderation.Denied");
        return "message";
    }
}
