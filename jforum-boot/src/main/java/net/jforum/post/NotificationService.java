package net.jforum.post;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.Forum;
import net.jforum.domain.ForumWatch;
import net.jforum.domain.Post;
import net.jforum.domain.Topic;
import net.jforum.domain.TopicWatch;
import net.jforum.domain.User;
import net.jforum.repository.ForumWatchRepository;
import net.jforum.repository.TopicWatchRepository;
import net.jforum.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Watch notifications (legacy TopicsCommon.notifyUsers / ForumAction watchers, EmailSenderTask).
 * Mails are sent asynchronously and failures are only logged, never breaking the post flow.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final TopicWatchRepository topicWatches;
    private final ForumWatchRepository forumWatches;
    private final UserRepository users;
    private final ForumConfig config;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public NotificationService(ObjectProvider<JavaMailSender> mailSender, TopicWatchRepository topicWatches,
                               ForumWatchRepository forumWatches, UserRepository users, ForumConfig config) {
        this.mailSender = mailSender;
        this.topicWatches = topicWatches;
        this.forumWatches = forumWatches;
        this.users = users;
        this.config = config;
    }

    private boolean enabled() {
        return "true".equalsIgnoreCase(config.get(ConfigKeys.MAIL_NOTIFY_ANSWERS, "true"));
    }

    /** Notify topic watchers about a new reply; like legacy, only watchers that read the topic since the last mail. */
    public void notifyTopicWatchers(Topic topic, Post post) {
        if (!enabled()) {
            return;
        }
        List<User> recipients = new ArrayList<>();
        for (TopicWatch w : topicWatches.findByTopicId(topic.getId())) {
            if (w.getUserId() == null || w.getUserId().equals(post.getUserId())) {
                continue;
            }
            users.findById(w.getUserId()).ifPresent(u -> {
                if (w.isRead() || u.isNotifyAlways()) {
                    recipients.add(u);
                    w.setRead(false);
                    topicWatches.save(w);
                }
            });
        }
        String link = link() + "/posts/preList/" + topic.getId() + "/" + post.getId() + ".page";
        String subject = MessageFormat.format(subjectTemplate("mail.newAnswer.subject", "Message posted: {0}"), topic.getTitle());
        String body = "Hello,\n\nA new reply was posted by " + post.getUsername() + " to the topic \"" + topic.getTitle()
            + "\".\n\nRead it at: " + link + "\n\nUnwatch: " + link() + "/posts/unwatch/" + topic.getId() + ".page\n";
        send(recipients, subject, body);
    }

    /** Notify forum watchers about a new topic. */
    public void notifyForumWatchers(Forum forum, Topic topic, Post post) {
        if (!enabled()) {
            return;
        }
        List<User> recipients = new ArrayList<>();
        for (ForumWatch w : forumWatches.findByForumId(forum.getId())) {
            if (w.getUserId() != null && !w.getUserId().equals(post.getUserId())) {
                users.findById(w.getUserId()).ifPresent(recipients::add);
            }
        }
        String subject = MessageFormat.format(subjectTemplate("mail.newTopic.subject", "New Topic: {0}"), topic.getTitle());
        String body = "Hello,\n\nA new topic \"" + topic.getTitle() + "\" was created by " + post.getUsername()
            + " in the forum \"" + forum.getName() + "\".\n\nRead it at: " + link() + "/posts/list/" + topic.getId() + ".page\n";
        send(recipients, subject, body);
    }

    private String subjectTemplate(String key, String fallback) {
        return config.get(key, fallback).replace("${forum.name}", config.forumName()).replace("'", "''");
    }

    private String link() {
        String l = config.get(ConfigKeys.FORUM_LINK, "");
        return l.endsWith("/") ? l.substring(0, l.length() - 1) : l;
    }

    private void send(List<User> recipients, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || recipients.isEmpty()) {
            return;
        }
        String from = config.get(ConfigKeys.MAIL_SENDER, "jforum@localhost");
        executor.submit(() -> {
            for (User u : recipients) {
                if (u.getEmail() == null || u.getEmail().isBlank()) {
                    continue;
                }
                try {
                    SimpleMailMessage m = new SimpleMailMessage();
                    m.setFrom(from);
                    m.setTo(u.getEmail());
                    m.setSubject(subject);
                    m.setText(body);
                    sender.send(m);
                } catch (RuntimeException e) {
                    log.warn("Could not send notification to {}: {}", u.getEmail(), e.getMessage());
                }
            }
        });
    }
}
