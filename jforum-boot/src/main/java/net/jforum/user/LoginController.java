package net.jforum.user;

import net.jforum.security.CurrentUser;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** GET /user/login.page; POST /user/validateLogin.page and /user/logout.page are handled by Spring Security. */
@Controller
public class LoginController {

    private final CurrentUser currentUser;

    public LoginController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/user/login.page")
    public String login(@RequestParam(required = false) String error, @RequestParam(required = false) String returnPath, Model model) {
        if (currentUser.isLogged()) {
            return "redirect:/forums/list.page";
        }
        model.addAttribute("invalidLogin", error != null);
        model.addAttribute("returnPath", returnPath);
        return "login";
    }
}
