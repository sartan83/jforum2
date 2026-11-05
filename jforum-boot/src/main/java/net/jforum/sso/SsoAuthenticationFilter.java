package net.jforum.sso;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import net.jforum.domain.User;
import net.jforum.security.ForumUserDetails;
import net.jforum.user.LoginSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Legacy ControllerUtils.checkSSO: when authentication.type=sso, every request is matched against the SSO
 * identity; a changed/new identity logs the user in (registering it on first access), a missing one logs out.
 * Registered inside the Spring Security chain (see SecurityConfig), not as a standalone servlet filter.
 */
@Component
public class SsoAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SsoAuthenticationFilter.class);

    private final SsoSettings settings;
    private final SsoUtils ssoUtils;
    private final LoginSupport loginSupport;
    private final ApplicationContext context;
    private final RemoteUserSSO defaultSso;

    public SsoAuthenticationFilter(SsoSettings settings, SsoUtils ssoUtils, LoginSupport loginSupport, ApplicationContext context,
                                   RemoteUserSSO defaultSso) {
        this.settings = settings;
        this.ssoUtils = ssoUtils;
        this.loginSupport = loginSupport;
        this.context = context;
        this.defaultSso = defaultSso;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (settings.isEnabled() && !isStatic(request)) {
            SSO sso = resolve();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String logged = auth != null && auth.getPrincipal() instanceof ForumUserDetails d ? d.getUsername() : null;
            if (!sso.isSessionValid(logged, request)) {
                String username = sso.authenticateUser(request);
                if (username == null) {
                    SecurityContextHolder.clearContext();
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                    }
                } else {
                    User user = ssoUtils.find(username).orElseGet(() ->
                        ssoUtils.register(username, attribute(request, settings.passwordAttribute(), settings.defaultPassword()),
                            attribute(request, settings.emailAttribute(), settings.defaultEmail())));
                    if (user.isActive() && !user.isDeleted()) {
                        loginSupport.login(user, request, response);
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isStatic(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.contains("/css/") || uri.contains("/js/") || uri.contains("/images/");
    }

    private static String attribute(HttpServletRequest request, String name, String fallback) {
        HttpSession session = request.getSession(false);
        Object v = session == null ? null : session.getAttribute(name);
        if (v == null) {
            v = request.getAttribute(name);
        }
        return v == null || v.toString().isBlank() ? fallback : v.toString();
    }

    SSO resolve() {
        String impl = settings.implementation();
        if (impl == null || impl.isBlank() || RemoteUserSSO.LEGACY_CLASS.equals(impl) || RemoteUserSSO.class.getName().equals(impl)) {
            return defaultSso;
        }
        try {
            Class<?> type = Class.forName(impl);
            if (SSO.class.isAssignableFrom(type)) {
                var beans = context.getBeansOfType(type);
                if (!beans.isEmpty()) {
                    return (SSO) beans.values().iterator().next();
                }
                return (SSO) context.getAutowireCapableBeanFactory().createBean(type);
            }
            log.warn("sso.implementation {} does not implement {}", impl, SSO.class.getName());
        } catch (Exception e) {
            log.warn("Cannot load sso.implementation {}: {}", impl, e.toString());
        }
        return defaultSso;
    }
}
