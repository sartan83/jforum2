package net.jforum.security;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/** Honors the legacy hidden "returnPath" login field, restricted to local paths. */
public class LegacyLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public LegacyLoginSuccessHandler() {
        setDefaultTargetUrl("/forums/list.page");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
        throws ServletException, IOException {
        String returnPath = request.getParameter("returnPath");
        if (returnPath != null && returnPath.startsWith("/") && !returnPath.startsWith("//") && !returnPath.contains("login.page")) {
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, returnPath);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
