package net.jforum.boot;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.MultipartConfigElement;

import net.jforum.ForumSessionListener;
import net.jforum.JForum;
import net.jforum.util.legacy.clickstream.ClickstreamFilter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

/**
 * Former WEB-INF/web.xml: the JForum front controller keeps serving every
 * <code>*.page</code> URL (urlPattern.properties / modulesMapping.properties),
 * while template assets and images are served as static resources
 * (attachment thumbnails come from GridFS, see AttachmentThumbnailController).
 */
@Configuration
public class JForumWebConfig implements WebMvcConfigurer
{
	private final JForumEnvironment environment;

	public JForumWebConfig(JForumEnvironment environment)
	{
		this.environment = environment;
	}

	@Bean
	public ServletRegistrationBean<JForum> jforumServlet()
	{
		ServletRegistrationBean<JForum> bean = new ServletRegistrationBean<>(new JForum(), "*.page");
		bean.setName("jforum");
		bean.setLoadOnStartup(1);
		bean.addInitParameter("development", "false");
		bean.setMultipartConfig(new MultipartConfigElement(""));
		return bean;
	}

	@Bean
	public FilterRegistrationBean<ClickstreamFilter> clickstreamFilter()
	{
		FilterRegistrationBean<ClickstreamFilter> bean = new FilterRegistrationBean<>(new ClickstreamFilter());
		bean.addUrlPatterns("*.page");
		return bean;
	}

	@Bean
	public ServletListenerRegistrationBean<ForumSessionListener> forumSessionListener()
	{
		return new ServletListenerRegistrationBean<>(new ForumSessionListener());
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry)
	{
		// was index.htm
		registry.addRedirectViewController("/", "/forums/list.page");
		registry.addRedirectViewController("/index.htm", "/forums/list.page");
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry)
	{
		String home = this.environment.getHome().toURI().toString();

		registry.addResourceHandler("/templates/**")
			.addResourceLocations(home + "templates/")
			.resourceChain(true)
			.addResolver(new StaticAssetResolver());
		registry.addResourceHandler("/images/**").addResourceLocations(home + "images/");
	}

	/**
	 * Only serves the static assets of the template directories (css, js, images),
	 * never the FreeMarker sources.
	 */
	static class StaticAssetResolver extends PathResourceResolver
	{
		private static final Set<String> EXTENSIONS = Set.of("css", "js", "gif", "png", "jpg", "jpeg", "ico", "swf");

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException
		{
			String extension = StringUtils.getFilenameExtension(resourcePath);

			if (extension == null || !EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
				return null;
			}

			return super.getResource(resourcePath, location);
		}
	}
}
