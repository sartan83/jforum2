package net.jforum.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import net.jforum.api.UserRestController;
import net.jforum.pm.PrivateMessageController;
import net.jforum.web.ForbiddenException;
import net.jforum.web.GlobalModelAdvice;
import net.jforum.web.NotFoundException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 403/404 for the user, PM, karma and bookmark controllers: like the shared ErrorAdvice, but the exception-handler
 * model is filled with the global layout variables (I18n, currentUser, ...) that @ModelAttribute does not provide there.
 */
@ControllerAdvice(assignableTypes = {UserController.class, LoginController.class, KarmaController.class, BookmarkController.class,
    PrivateMessageController.class, UserRestController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserErrorAdvice {

    private final GlobalModelAdvice globalModel;

    public UserErrorAdvice(GlobalModelAdvice globalModel) {
        this.globalModel = globalModel;
    }

    @ExceptionHandler(NotFoundException.class)
    public String notFound(NotFoundException e, Model model, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        globalModel.common(model, request);
        model.addAttribute("messageKey", e.getMessage());
        return "message";
    }

    @ExceptionHandler(ForbiddenException.class)
    public String forbidden(ForbiddenException e, Model model, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        globalModel.common(model, request);
        model.addAttribute("messageKey", e.getMessage());
        return "message";
    }
}
