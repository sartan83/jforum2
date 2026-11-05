package net.jforum.topic;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import net.jforum.domain.Topic;
import net.jforum.domain.User;

/** Session based read markers, like legacy SessionFacade topicsReadTime / topicsReadTimeByForum. */
public final class ReadTracker {

    private static final String TOPICS = "topicsReadTime";
    private static final String FORUMS = "topicsReadTimeByForum";

    private ReadTracker() {
    }

    public static void markTopicRead(HttpSession session, int topicId) {
        map(session, TOPICS).put(topicId, System.currentTimeMillis());
    }

    public static void markForumRead(HttpSession session, int forumId) {
        map(session, FORUMS).put(forumId, System.currentTimeMillis());
    }

    /** Legacy TopicsCommon.prepareTopics: a topic is unread if it changed after the last visit and was not read since. */
    public static boolean isRead(HttpSession session, User user, boolean logged, Topic topic) {
        if (!logged || topic.getLastPostTime() == null) {
            return true;
        }
        long lastPost = topic.getLastPostTime().toEpochMilli();
        Instant lastVisit = user.getLastVisit();
        if (lastVisit != null && lastPost <= lastVisit.toEpochMilli()) {
            return true;
        }
        Long forumRead = map(session, FORUMS).get(topic.getForumId());
        if (forumRead != null && forumRead >= lastPost) {
            return true;
        }
        Long topicRead = map(session, TOPICS).get(topic.getId());
        return topicRead != null && topicRead >= lastPost;
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Long> map(HttpSession session, String name) {
        Object o = session.getAttribute(name);
        if (o instanceof HashMap<?, ?> m) {
            return (Map<Integer, Long>) m;
        }
        HashMap<Integer, Long> m = new HashMap<>();
        session.setAttribute(name, m);
        return m;
    }

    /** Re-store the maps so external session stores persist the changes. */
    public static void flush(HttpSession session) {
        session.setAttribute(TOPICS, map(session, TOPICS));
        session.setAttribute(FORUMS, map(session, FORUMS));
    }
}
