package net.jforum.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import net.jforum.domain.User;
import net.jforum.i18n.I18n;
import net.jforum.repository.ApiKeyRepository;
import net.jforum.repository.UserRepository;
import net.jforum.user.UserService;

import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Legacy module "userApi" (net.jforum.api.rest.UserREST): /userApi/list.page and /userApi/insert.page,
 * authenticated by the "api_key" parameter (api_keys collection, valid until "validity").
 * Responses use the legacy XML format (templates/default/api/*.xml); JSON with ?format=json or Accept: application/json.
 */
@RestController
public class UserRestController {

    private final ApiKeyRepository apiKeys;
    private final UserRepository users;
    private final UserService userService;
    private final I18n i18n;

    public UserRestController(ApiKeyRepository apiKeys, UserRepository users, UserService userService, I18n i18n) {
        this.apiKeys = apiKeys;
        this.users = users;
        this.userService = userService;
        this.i18n = i18n;
    }

    @RequestMapping(value = "/userApi/list.page", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> list(HttpServletRequest request) {
        try {
            authenticate(request);
            List<User> all = users.findAll(Sort.by("id"));
            if (json(request)) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (User u : all) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("email", u.getEmail());
                    list.add(m);
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("response-code", "OK");
                body.put("total", all.size());
                body.put("users", list);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
            }
            StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<jforum-api response-code=\"OK\">\n");
            sb.append("\t<users total=\"").append(all.size()).append("\">\n");
            for (User u : all) {
                sb.append("\t\t<user id=\"").append(u.getId()).append("\" username=\"").append(esc(u.getUsername()))
                    .append("\" email=\"").append(esc(u.getEmail())).append("\"/>\n");
            }
            sb.append("\t</users>\n</jforum-api>");
            return xml(sb.toString());
        } catch (ApiException e) {
            return error(request, e.getMessage());
        }
    }

    /** Required parameters: username, email, password (legacy contract: a 32-char hex value is a legacy MD5 hash). */
    @RequestMapping(value = "/userApi/insert.page", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> insert(HttpServletRequest request) {
        try {
            authenticate(request);
            String username = required(request, "username").trim();
            String email = required(request, "email").trim();
            String password = required(request, "password");
            String error = userService.validateNewUser(username, email);
            if (error != null) {
                throw new ApiException(error);
            }
            User u = password.matches("[0-9a-fA-F]{32}")
                ? userService.createWithHash(username, password.toLowerCase(), email, false)
                : userService.create(username, password, email, false);
            if (json(request)) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("response-code", "OK");
                body.put("user", Map.of("id", u.getId()));
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return xml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<jforum-api response-code=\"OK\">\n\t<user id=\""
                + u.getId() + "\"/>\n</jforum-api>");
        } catch (ApiException e) {
            return error(request, e.getMessage());
        }
    }

    private void authenticate(HttpServletRequest request) {
        String key = required(request, "api_key");
        boolean valid = apiKeys.findByApiKey(key)
            .filter(k -> k.getValidity() == null || !k.getValidity().isBefore(Instant.now()))
            .isPresent();
        if (!valid) {
            throw new ApiException("The provided API authentication information is not valid");
        }
    }

    private static String required(HttpServletRequest request, String name) {
        String v = request.getParameter(name);
        if (v == null || v.trim().isEmpty()) {
            throw new ApiException("The parameter '" + name + "' was not found");
        }
        return v;
    }

    private static boolean json(HttpServletRequest request) {
        if ("json".equalsIgnoreCase(request.getParameter("format"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json") && !accept.contains("xml");
    }

    private ResponseEntity<?> error(HttpServletRequest request, String message) {
        if (json(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("response-code", "ERROR");
            body.put("message", message);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return xml("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<jforum-api response-code=\"ERROR\">\n\t<message>\n\t\t<![CDATA[\n\t\t"
            + message.replace("]]>", "]]]]><![CDATA[>") + "\n\t\t]]>\n\t</message>\n</jforum-api>");
    }

    private static ResponseEntity<String> xml(String body) {
        return ResponseEntity.ok().contentType(new MediaType("text", "xml", java.nio.charset.StandardCharsets.UTF_8)).body(body);
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    static class ApiException extends RuntimeException {
        ApiException(String message) {
            super(message);
        }
    }
}
