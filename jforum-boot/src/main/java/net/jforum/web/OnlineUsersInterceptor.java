package net.jforum.web;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import net.jforum.domain.User;
import net.jforum.domain.UserSession;
import net.jforum.repository.UserSessionRepository;
import net.jforum.security.CurrentUser;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Tracks "who is online" per HTTP session (legacy SessionFacade + jforum_sessions). */
@Configuration
public class OnlineUsersInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final UserSessionRepository sessions;
    private final CurrentUser currentUser;

    public OnlineUsersInterceptor(UserSessionRepository sessions, CurrentUser currentUser) {
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/**")
            .excludePathPatterns("/css/**", "/js/**", "/images/**", "/actuator/**", "/error");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String id = request.getSession(true).getId();
        User user = currentUser.get();
        UserSession us = sessions.findById(id).orElseGet(() -> {
            UserSession s = new UserSession();
            s.setId(id);
            s.setStartTime(Instant.now());
            return s;
        });
        us.setUserId(user.getId());
        us.setUsername(user.getUsername());
        us.setLogged(currentUser.isLogged());
        us.setIp(request.getRemoteAddr());
        us.setLastAccess(Instant.now());
        sessions.save(us);
        return true;
    }
}
