package net.jforum.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

/**
 * Supports the legacy query-string front controller: /jforum.page?module=posts&amp;action=list&amp;topic_id=3
 * is redirected (GET) or forwarded (POST) to the friendly URL /posts/list/3.page, using the parameter order
 * declared in jforum/urlPattern.properties. Unknown combinations go to /module/action.page with the
 * original query string. "/" and "/jforum.page" without module go to the forum index.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class LegacyUrlFilter extends OncePerRequestFilter {

    private final Map<String, List<List<String>>> patterns = new HashMap<>();

    public LegacyUrlFilter() {
        Properties p = new Properties();
        try (InputStream in = new ClassPathResource("jforum/urlPattern.properties").getInputStream()) {
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (String key : p.stringPropertyNames()) {
            int dot = key.lastIndexOf('.');
            String moduleAction = key.substring(0, dot);
            String value = p.getProperty(key).trim();
            List<String> params = new ArrayList<>();
            if (!value.isEmpty()) {
                for (String s : value.split(",")) {
                    params.add(s.trim());
                }
            }
            patterns.computeIfAbsent(moduleAction, k -> new ArrayList<>()).add(params);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("/".equals(path) || "".equals(path)) {
            response.sendRedirect(request.getContextPath() + "/forums/list.page");
            return;
        }
        if (!"/jforum.page".equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        String module = request.getParameter("module");
        String action = request.getParameter("action");
        if (module == null || module.isBlank()) {
            module = "forums";
            action = "list";
        }
        if (action == null || action.isBlank()) {
            action = "list";
        }
        if (!module.matches("[A-Za-z]+") || !action.matches("[A-Za-z]+")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String target = resolve(module, action, request);
        if ("GET".equals(request.getMethod())) {
            response.sendRedirect(request.getContextPath() + target);
        } else {
            request.getRequestDispatcher(target).forward(request, response);
        }
    }

    /** Builds the friendly URL for module/action using the longest pattern whose parameters are all present. */
    String resolve(String module, String action, HttpServletRequest request) {
        List<String> best = null;
        for (List<String> params : patterns.getOrDefault(module + "." + action, List.of())) {
            boolean all = params.stream().allMatch(n -> request.getParameter(n) != null && !request.getParameter(n).isBlank());
            if (all && (best == null || params.size() > best.size())) {
                best = params;
            }
        }
        StringBuilder sb = new StringBuilder("/").append(module).append('/').append(action);
        List<String> used = best == null ? List.of() : best;
        for (String n : used) {
            sb.append('/').append(UriUtils.encodePathSegment(request.getParameter(n), "UTF-8"));
        }
        sb.append(".page");
        StringBuilder qs = new StringBuilder();
        request.getParameterMap().forEach((k, vs) -> {
            if (!k.equals("module") && !k.equals("action") && !used.contains(k)) {
                for (String v : vs) {
                    qs.append(qs.isEmpty() ? '?' : '&').append(UriUtils.encodeQueryParam(k, "UTF-8")).append('=')
                        .append(UriUtils.encodeQueryParam(v, "UTF-8"));
                }
            }
        });
        return sb.append(qs).toString();
    }
}
