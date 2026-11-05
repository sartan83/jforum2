package net.jforum.user;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the legacy plain-text mails (templates/mail/*.txt, copied to classpath:mail/) in the background,
 * like legacy Executor + EmailSenderTask. Failures are logged and never break the web flow.
 */
@Component
public class ForumMailer {

    private static final Logger log = LoggerFactory.getLogger(ForumMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ForumConfig config;
    private final Configuration freemarker;

    public ForumMailer(ObjectProvider<JavaMailSender> mailSender, ForumConfig config) {
        this.mailSender = mailSender;
        this.config = config;
        this.freemarker = new Configuration(Configuration.VERSION_2_3_32);
        freemarker.setClassForTemplateLoading(ForumMailer.class, "/mail");
        freemarker.setDefaultEncoding("UTF-8");
        freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    public void send(String to, String subjectKey, String template, Map<String, Object> model) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || to == null || to.isBlank()) {
            return;
        }
        Map<String, Object> ctx = new HashMap<>(model);
        ctx.putIfAbsent("forumName", config.forumName());
        String subject = config.get(subjectKey, "[" + config.forumName() + "]");
        String text;
        try {
            Template t = freemarker.getTemplate(template);
            StringWriter out = new StringWriter();
            t.process(ctx, out);
            text = out.toString();
        } catch (Exception e) {
            log.warn("Cannot render mail template {}: {}", template, e.toString());
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(config.get(ConfigKeys.MAIL_SENDER, "jforum@localhost"));
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        Thread.ofVirtual().name("jforum-mail").start(() -> {
            try {
                sender.send(message);
            } catch (Exception e) {
                log.warn("Cannot send mail '{}' to {}: {}", subject, to, e.toString());
            }
        });
    }

    /** Absolute URL for a forum path, based on legacy forum.link. */
    public String absoluteUrl(String path) {
        String link = config.get(ConfigKeys.FORUM_LINK, "http://localhost:8080/");
        if (link.endsWith("/")) {
            link = link.substring(0, link.length() - 1);
        }
        return link + path;
    }
}
