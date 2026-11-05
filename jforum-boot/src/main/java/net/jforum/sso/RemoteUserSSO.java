package net.jforum.sso;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestWrapper;
import org.springframework.stereotype.Component;

/**
 * Legacy RemoteUserSSO: trusts the container/proxy authenticated user (request.getRemoteUser()), or the
 * header configured in sso.remoteUser.header when the app runs behind an authenticating reverse proxy.
 */
@Component
public class RemoteUserSSO implements SSO {

    public static final String LEGACY_CLASS = "net.jforum.sso.RemoteUserSSO";

    private final SsoSettings settings;

    public RemoteUserSSO(SsoSettings settings) {
        this.settings = settings;
    }

    @Override
    public String authenticateUser(HttpServletRequest request) {
        String user = containerRequest(request).getRemoteUser();
        String header = settings.remoteUserHeader();
        if ((user == null || user.isBlank()) && header != null && !header.isBlank()) {
            user = request.getHeader(header);
        }
        return user == null || user.isBlank() ? null : user.trim();
    }

    /** Spring Security's request wrapper reports the forum login as remote user: ask the container request instead. */
    private static HttpServletRequest containerRequest(HttpServletRequest request) {
        HttpServletRequest r = request;
        while (r instanceof SecurityContextHolderAwareRequestWrapper w && w.getRequest() instanceof HttpServletRequest inner) {
            r = inner;
        }
        return r;
    }

    @Override
    public boolean isSessionValid(String loggedUsername, HttpServletRequest request) {
        String remoteUser = authenticateUser(request);
        if (remoteUser == null) {
            return loggedUsername == null;
        }
        return remoteUser.equals(loggedUsername);
    }
}
