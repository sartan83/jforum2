package net.jforum.sso;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pluggable single sign-on (legacy net.jforum.sso.SSO), selected by sso.implementation when
 * authentication.type=sso. Implementations must have a public no-arg constructor or be Spring beans.
 */
public interface SSO {

    /** @return the username of the authenticated user, or null if the request is not authenticated */
    String authenticateUser(HttpServletRequest request);

    /**
     * @param loggedUsername username of the current forum session, or null when anonymous
     * @return whether the current forum session still matches the SSO identity
     */
    boolean isSessionValid(String loggedUsername, HttpServletRequest request);
}
