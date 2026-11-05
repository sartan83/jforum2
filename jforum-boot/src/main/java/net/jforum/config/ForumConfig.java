package net.jforum.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.jforum.domain.ConfigEntry;
import net.jforum.repository.ConfigEntryRepository;

import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Replacement for legacy SystemGlobals. Resolution order: Mongo "config" collection (admin overrides),
 * Spring Environment property "jforum.&lt;key&gt;", then defaults from jforum/SystemGlobals.properties.
 * Values support ${key} expansion.
 */
@Component
public class ForumConfig {

    private final Properties defaults = new Properties();
    private final Map<String, String> overrides = new ConcurrentHashMap<>();
    private final ConfigEntryRepository repository;
    private final Environment environment;
    private final VariableExpander expander = new VariableExpander(this::raw);

    public ForumConfig(ConfigEntryRepository repository, Environment environment) {
        this.repository = repository;
        this.environment = environment;
        try (InputStream in = new ClassPathResource("jforum/SystemGlobals.properties").getInputStream()) {
            defaults.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        reload();
    }

    public final void reload() {
        overrides.clear();
        repository.findAll().forEach(c -> overrides.put(c.getId(), c.getValue() == null ? "" : c.getValue()));
    }

    private String raw(String key) {
        String v = overrides.get(key);
        if (v == null) {
            v = environment.getProperty("jforum." + key);
        }
        if (v == null) {
            v = defaults.getProperty(key);
        }
        return v == null ? null : v.trim();
    }

    public String get(String key) {
        return expander.expand(raw(key));
    }

    public String get(String key, String fallback) {
        String v = get(key);
        return v == null || v.isEmpty() ? fallback : v;
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public boolean getBool(String key) {
        return "true".equalsIgnoreCase(get(key));
    }

    public void set(String key, String value) {
        ConfigEntry entry = new ConfigEntry();
        entry.setId(key);
        entry.setValue(value);
        repository.save(entry);
        overrides.put(key, value);
    }

    /** All known keys with their effective (expanded) values, for the admin configuration page. */
    public Map<String, String> all() {
        Map<String, String> all = new TreeMap<>();
        defaults.stringPropertyNames().forEach(k -> all.put(k, get(k)));
        overrides.keySet().forEach(k -> all.put(k, get(k)));
        return all;
    }

    public String forumName() {
        return get(ConfigKeys.FORUM_NAME, "JForum");
    }

    public int topicsPerPage() {
        return getInt(ConfigKeys.TOPICS_PER_PAGE, 15);
    }

    public int postsPerPage() {
        return getInt(ConfigKeys.POSTS_PER_PAGE, 15);
    }
}
