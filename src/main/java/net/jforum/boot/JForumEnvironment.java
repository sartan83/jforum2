package net.jforum.boot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.annotation.PostConstruct;

import net.jforum.util.preferences.SystemGlobals;

import org.apache.log4j.Logger;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Replaces the old <code>ConfigLoader.startSystemglobals()</code>/<code>ForumStartup</code> bootstrap.
 * <ul>
 * <li>Every <code>jforum.*</code> property of the Spring environment (<code>application.yml</code>,
 * profiles, command line, ...) becomes a SystemGlobals key, without the prefix: <code>jforum.topics.per.page</code>
 * is <code>topics.per.page</code>, exactly as in the old SystemGlobals.properties.</li>
 * <li>The legacy code reads its configuration files and templates from the file system
 * (<code>${application.path}/WEB-INF/config</code>, <code>${application.path}/templates</code>), so the
 * classpath copies are synchronized at startup into <code>jforum.home</code>, which becomes the
 * application path. Uploaded files (attachments, avatars) are stored there too.</li>
 * </ul>
 */
@Component
public class JForumEnvironment
{
	private static final Logger logger = Logger.getLogger(JForumEnvironment.class);

	public static final String PREFIX = "jforum.";
	public static final String HOME = "jforum.home";

	private final ConfigurableEnvironment environment;
	private File home;

	public JForumEnvironment(ConfigurableEnvironment environment)
	{
		this.environment = environment;
	}

	@PostConstruct
	public void start() throws IOException
	{
		this.home = new File(this.environment.getProperty(HOME, "jforum-home")).getAbsoluteFile();
		logger.info("JForum home: " + this.home);

		this.copyClasspathTree("config", new File(this.home, "WEB-INF/config"));
		this.copyClasspathTree("templates", new File(this.home, "templates"));
		this.copyClasspathTree("images", new File(this.home, "images"));

		SystemGlobals.initGlobals(this.home.getPath(), this.jforumProperties());
	}

	public File getHome()
	{
		return this.home;
	}

	/**
	 * Raw (unresolved) values: <code>${...}</code> placeholders are expanded by SystemGlobals itself,
	 * which also takes the admin overrides into account.
	 */
	Properties jforumProperties()
	{
		List<PropertySource<?>> sources = new ArrayList<>();
		this.environment.getPropertySources().forEach(sources::add);

		Properties p = new Properties();

		for (int i = sources.size() - 1; i >= 0; i--) {
			if (sources.get(i) instanceof EnumerablePropertySource<?> source) {
				for (String name : source.getPropertyNames()) {
					if (name.startsWith(PREFIX) && !name.equals(HOME)) {
						Object value = source.getProperty(name);
						p.setProperty(name.substring(PREFIX.length()), value == null ? "" : value.toString());
					}
				}
			}
		}

		return p;
	}

	private void copyClasspathTree(String root, File target) throws IOException
	{
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		String rootUrl = resolver.getResource("classpath:" + root + "/").getURL().toString();

		for (Resource resource : resolver.getResources("classpath:" + root + "/**")) {
			if (!resource.isReadable()) {
				continue;
			}

			String url = resource.getURL().toString();

			if (!url.startsWith(rootUrl) || url.endsWith("/")) {
				continue;
			}

			Path destination = target.toPath().resolve(url.substring(rootUrl.length()));
			Files.createDirectories(destination.getParent());

			try (InputStream in = resource.getInputStream()) {
				Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}
