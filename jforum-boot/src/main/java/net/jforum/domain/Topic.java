package net.jforum.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A discussion topic (legacy jforum_topics). Polls are embedded. */
@Document("topics")
public class Topic {

    @Id private Integer id;
    @Indexed private Integer forumId;
    private String title;
    @Indexed private Integer userId;
    private String username;
    private Instant time;
    private int views;
    private int replies;
    private TopicStatus status = TopicStatus.UNLOCKED;
    private TopicType type = TopicType.NORMAL;
    private Integer firstPostId;
    private Integer lastPostId;
    private Instant lastPostTime;
    private Integer lastPostUserId;
    private String lastPostUsername;
    private boolean moderated;
    private boolean hasAttach;
    private Integer movedId;
    private Poll poll;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getForumId() {
        return forumId;
    }

    public void setForumId(Integer forumId) {
        this.forumId = forumId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public int getViews() {
        return views;
    }

    public void setViews(int views) {
        this.views = views;
    }

    public int getReplies() {
        return replies;
    }

    public void setReplies(int replies) {
        this.replies = replies;
    }

    public TopicStatus getStatus() {
        return status;
    }

    public void setStatus(TopicStatus status) {
        this.status = status;
    }

    public TopicType getType() {
        return type;
    }

    public void setType(TopicType type) {
        this.type = type;
    }

    public Integer getFirstPostId() {
        return firstPostId;
    }

    public void setFirstPostId(Integer firstPostId) {
        this.firstPostId = firstPostId;
    }

    public Integer getLastPostId() {
        return lastPostId;
    }

    public void setLastPostId(Integer lastPostId) {
        this.lastPostId = lastPostId;
    }

    public Instant getLastPostTime() {
        return lastPostTime;
    }

    public void setLastPostTime(Instant lastPostTime) {
        this.lastPostTime = lastPostTime;
    }

    public Integer getLastPostUserId() {
        return lastPostUserId;
    }

    public void setLastPostUserId(Integer lastPostUserId) {
        this.lastPostUserId = lastPostUserId;
    }

    public String getLastPostUsername() {
        return lastPostUsername;
    }

    public void setLastPostUsername(String lastPostUsername) {
        this.lastPostUsername = lastPostUsername;
    }

    public boolean isModerated() {
        return moderated;
    }

    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    public boolean isHasAttach() {
        return hasAttach;
    }

    public void setHasAttach(boolean hasAttach) {
        this.hasAttach = hasAttach;
    }

    public Integer getMovedId() {
        return movedId;
    }

    public void setMovedId(Integer movedId) {
        this.movedId = movedId;
    }

    public Poll getPoll() {
        return poll;
    }

    public void setPoll(Poll poll) {
        this.poll = poll;
    }
}
