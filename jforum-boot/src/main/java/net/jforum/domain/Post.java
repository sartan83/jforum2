package net.jforum.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A post (legacy jforum_posts + jforum_posts_text). Separate collection so topics can be paginated. */
@Document("posts")
public class Post {

    @Id private Integer id;
    private Integer topicId;
    @Indexed private Integer forumId;
    @Indexed private Integer userId;
    private String username;
    private Instant time;
    private String userIp;
    @TextIndexed(weight = 2) private String subject;
    @TextIndexed private String text;
    private boolean bbCodeEnabled = true;
    private boolean htmlEnabled;
    private boolean smiliesEnabled = true;
    private boolean signatureEnabled = true;
    private Instant editTime;
    private int editCount;
    private boolean moderate;
    private boolean hasAttachments;
    private int karma;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTopicId() {
        return topicId;
    }

    public void setTopicId(Integer topicId) {
        this.topicId = topicId;
    }

    public Integer getForumId() {
        return forumId;
    }

    public void setForumId(Integer forumId) {
        this.forumId = forumId;
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

    public String getUserIp() {
        return userIp;
    }

    public void setUserIp(String userIp) {
        this.userIp = userIp;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isBbCodeEnabled() {
        return bbCodeEnabled;
    }

    public void setBbCodeEnabled(boolean bbCodeEnabled) {
        this.bbCodeEnabled = bbCodeEnabled;
    }

    public boolean isHtmlEnabled() {
        return htmlEnabled;
    }

    public void setHtmlEnabled(boolean htmlEnabled) {
        this.htmlEnabled = htmlEnabled;
    }

    public boolean isSmiliesEnabled() {
        return smiliesEnabled;
    }

    public void setSmiliesEnabled(boolean smiliesEnabled) {
        this.smiliesEnabled = smiliesEnabled;
    }

    public boolean isSignatureEnabled() {
        return signatureEnabled;
    }

    public void setSignatureEnabled(boolean signatureEnabled) {
        this.signatureEnabled = signatureEnabled;
    }

    public Instant getEditTime() {
        return editTime;
    }

    public void setEditTime(Instant editTime) {
        this.editTime = editTime;
    }

    public int getEditCount() {
        return editCount;
    }

    public void setEditCount(int editCount) {
        this.editCount = editCount;
    }

    public boolean isModerate() {
        return moderate;
    }

    public void setModerate(boolean moderate) {
        this.moderate = moderate;
    }

    public boolean isHasAttachments() {
        return hasAttachments;
    }

    public void setHasAttachments(boolean hasAttachments) {
        this.hasAttachments = hasAttachments;
    }

    public int getKarma() {
        return karma;
    }

    public void setKarma(int karma) {
        this.karma = karma;
    }
}
