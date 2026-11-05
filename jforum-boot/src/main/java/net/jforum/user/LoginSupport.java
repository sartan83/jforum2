package net.jforum.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import net.jforum.domain.User;
import net.jforum.security.CurrentUser;
import net.jforum.security.ForumUserDetails;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Programmatic login (legacy SessionFacade.makeLogged), used after registration, activation and SSO. */
@Component
public class LoginSupport {

    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();
    private final CurrentUser currentUser;

    public LoginSupport(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public void login(User user, HttpServletRequest request, HttpServletResponse response) {
        ForumUserDetails details = new ForumUserDetails(user.getId(), user.getUsername(), user.getPassword(), true);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        repository.saveContext(ctx, request, response);
        currentUser.refresh();
    }
}
