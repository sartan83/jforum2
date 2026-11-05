package net.jforum.sso;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SsoConfig {

    /** The filter runs inside the Spring Security chain only. */
    @Bean
    FilterRegistrationBean<SsoAuthenticationFilter> ssoFilterRegistration(SsoAuthenticationFilter filter) {
        FilterRegistrationBean<SsoAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
