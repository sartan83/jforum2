package net.jforum.web;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Renders the legacy generic message page for 403/404 (message key resolved through I18n). */
@ControllerAdvice
public class ErrorAdvice {

    @ExceptionHandler(ForbiddenException.class)
    public String forbidden(ForbiddenException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("messageKey", e.getMessage());
        return "message";
    }

    @ExceptionHandler(NotFoundException.class)
    public String notFound(NotFoundException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("messageKey", e.getMessage());
        return "message";
    }
}
