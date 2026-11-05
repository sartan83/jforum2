package net.jforum.moderation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import net.jforum.web.ForbiddenException;
import net.jforum.web.GlobalModelAdvice;
import net.jforum.web.NotFoundException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Same as {@link net.jforum.web.ErrorAdvice} for the moderation/search/RSS/attachment controllers, but also
 * populates the common model ({@code @ModelAttribute} methods are not invoked for exception handlers, so
 * message.ftlh would otherwise miss I18n/contextPath).
 */
@ControllerAdvice(basePackages = {"net.jforum.moderation", "net.jforum.search", "net.jforum.rss", "net.jforum.attachment"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Track4ErrorAdvice {

    private final GlobalModelAdvice common;

    public Track4ErrorAdvice(GlobalModelAdvice common) {
        this.common = common;
    }

    @ExceptionHandler(ForbiddenException.class)
    public String forbidden(ForbiddenException e, Model model, HttpServletRequest request, HttpServletResponse response) {
        return render(HttpServletResponse.SC_FORBIDDEN, e.getMessage(), model, request, response);
    }

    @ExceptionHandler(NotFoundException.class)
    public String notFound(NotFoundException e, Model model, HttpServletRequest request, HttpServletResponse response) {
        return render(HttpServletResponse.SC_NOT_FOUND, e.getMessage(), model, request, response);
    }

    private String render(int status, String key, Model model, HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        common.common(model, request);
        model.addAttribute("messageKey", key);
        return "message";
    }
}
