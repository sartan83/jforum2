package net.jforum.sso;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;

import org.springframework.stereotype.Component;

/** Legacy authentication.type / sso.* keys. SSO is off unless authentication.type=sso. */
@Component
public class SsoSettings {

    public static final String TYPE_SSO = "sso";

    private final ForumConfig config;

    public SsoSettings(ForumConfig config) {
        this.config = config;
    }

    public boolean isEnabled() {
        return TYPE_SSO.equalsIgnoreCase(config.get(ConfigKeys.AUTHENTICATION_TYPE, "default"));
    }

    public String implementation() {
        return config.get(ConfigKeys.SSO_IMPLEMENTATION, RemoteUserSSO.LEGACY_CLASS);
    }

    public String redirect() {
        return config.get("sso.redirect", "");
    }

    public String forumLink() {
        return config.get(ConfigKeys.FORUM_LINK, "");
    }

    public String passwordAttribute() {
        return config.get("sso.password.attribute", "password");
    }

    public String emailAttribute() {
        return config.get("sso.email.attribute", "email");
    }

    public String defaultEmail() {
        return config.get("sso.default.email", "sso@user");
    }

    public String defaultPassword() {
        return config.get("sso.default.password", "sso");
    }

    /** Optional trusted header carrying the username (e.g. set by an authenticating reverse proxy). */
    public String remoteUserHeader() {
        return config.get("sso.remoteUser.header", "");
    }
}
