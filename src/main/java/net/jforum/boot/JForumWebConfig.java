package net.jforum.boot;

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

/**
 * Former WEB-INF/web.xml: the JForum front controller keeps serving every
 * <code>*.page</code> URL (urlPattern.properties / modulesMapping.properties),
 * while templates assets, images and uploads are served as static resources.
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

		registry.addResourceHandler("/templates/**/*.css", "/templates/**/*.js", "/templates/**/*.gif",
				"/templates/**/*.png", "/templates/**/*.jpg", "/templates/**/*.ico", "/templates/**/*.swf")
			.addResourceLocations(home + "templates/");
		registry.addResourceHandler("/images/**").addResourceLocations(home + "images/");
		registry.addResourceHandler("/upload/**").addResourceLocations(home + "upload/");
	}
}
