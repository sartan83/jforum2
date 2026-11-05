package net.jforum.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

/**
 * Form login on the legacy URLs: GET /user/login.page shows the form, POST /user/validateLogin.page
 * authenticates (fields "username", "password", "autologin"), /user/logout.page logs out.
 * Admin modules (/adm*) require perm_administration, evaluated live against the user's groups.
 * CSRF protection is enabled: every POST form must include the ${_csrf.parameterName} hidden field.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<CurrentUser> currentUser,
                                            UserDetailsService userDetailsService,
                                            @Value("${jforum.remember-me-key:jforum-remember-me}") String rememberMeKey) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(RegexRequestMatcher.regexMatcher("^/adm[A-Za-z]*/.*"))
                    .access((authentication, ctx) -> new AuthorizationDecision(currentUser.getObject().isAdministrator()))
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginPage("/user/login.page")
                .loginProcessingUrl("/user/validateLogin.page")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(new LegacyLoginSuccessHandler())
                .failureUrl("/user/login.page?error=1")
                .permitAll())
            .rememberMe(rm -> rm
                .key(rememberMeKey)
                .rememberMeParameter("autologin")
                .userDetailsService(userDetailsService))
            .logout(logout -> logout
                .logoutRequestMatcher(request -> "/user/logout.page".equals(request.getRequestURI().substring(request.getContextPath().length())))
                .logoutSuccessUrl("/forums/list.page"))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/userApi/**"))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }
}
