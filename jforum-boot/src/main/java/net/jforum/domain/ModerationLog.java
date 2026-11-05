package net.jforum.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A moderation activity log entry (legacy jforum_moderation_log). */
@Document("moderation_log")
public class ModerationLog {

    @Id private Integer id;
    private Integer userId;
    private String username;
    private String description;
    private String originalMessage;
    private Instant date;
    private ModerationLogType type;
    private Integer postId;
    private Integer topicId;
    private Integer postUserId;
    private String postUsername;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public ModerationLogType getType() {
        return type;
    }

    public void setType(ModerationLogType type) {
        this.type = type;
    }

    public Integer getPostId() {
        return postId;
    }

    public void setPostId(Integer postId) {
        this.postId = postId;
    }

    public Integer getTopicId() {
        return topicId;
    }

    public void setTopicId(Integer topicId) {
        this.topicId = topicId;
    }

    public Integer getPostUserId() {
        return postUserId;
    }

    public void setPostUserId(Integer postUserId) {
        this.postUserId = postUserId;
    }

    public String getPostUsername() {
        return postUsername;
    }

    public void setPostUsername(String postUsername) {
        this.postUsername = postUsername;
    }
}
