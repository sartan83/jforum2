package net.jforum.topic;

import net.jforum.domain.Forum;
import net.jforum.domain.Topic;

/** Topic plus the computed flags legacy TopicsCommon.prepareTopics puts in the context. */
public class TopicView {

    private final Topic topic;
    private final Forum forum;
    private final boolean read;
    private final boolean hot;
    private final int totalPages;
    private final int postsPerPage;

    public TopicView(Topic topic, Forum forum, boolean read, boolean hot, int postsPerPage) {
        this.topic = topic;
        this.forum = forum;
        this.read = read;
        this.hot = hot;
        this.postsPerPage = postsPerPage;
        this.totalPages = (int) Math.ceil((topic.getReplies() + 1) / (double) postsPerPage);
    }

    public Topic getTopic() {
        return topic;
    }

    public Forum getForum() {
        return forum;
    }

    public boolean isRead() {
        return read;
    }

    public boolean isHot() {
        return hot;
    }

    public boolean isLocked() {
        return topic.getStatus() == net.jforum.domain.TopicStatus.LOCKED;
    }

    public boolean isSticky() {
        return topic.getType() == net.jforum.domain.TopicType.STICKY;
    }

    public boolean isAnnounce() {
        return topic.getType() == net.jforum.domain.TopicType.ANNOUNCE;
    }

    public boolean isHasPoll() {
        return topic.getPoll() != null;
    }

    public int getTotalPages() {
        return totalPages;
    }

    /** Start offset of the given 1-based page of this topic. */
    public int pageStart(int page) {
        return (page - 1) * postsPerPage;
    }
}
