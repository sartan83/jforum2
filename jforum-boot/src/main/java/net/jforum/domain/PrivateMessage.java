package net.jforum.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A private message. As in legacy JForum one copy is stored per mailbox (recipient inbox + sender sentbox). */
@Document("private_messages")
public class PrivateMessage {

    @Id private Integer id;
    private PrivateMessageType type;
    @Indexed private Integer fromUserId;
    @Indexed private Integer toUserId;
    private String subject;
    private String text;
    private Instant date;
    private String fromIp;
    private boolean bbCodeEnabled = true;
    private boolean htmlEnabled;
    private boolean smiliesEnabled = true;
    private boolean signatureEnabled = true;
    private boolean hasAttachments;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public PrivateMessageType getType() {
        return type;
    }

    public void setType(PrivateMessageType type) {
        this.type = type;
    }

    public Integer getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Integer fromUserId) {
        this.fromUserId = fromUserId;
    }

    public Integer getToUserId() {
        return toUserId;
    }

    public void setToUserId(Integer toUserId) {
        this.toUserId = toUserId;
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

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public String getFromIp() {
        return fromIp;
    }

    public void setFromIp(String fromIp) {
        this.fromIp = fromIp;
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

    public boolean isHasAttachments() {
        return hasAttachments;
    }

    public void setHasAttachments(boolean hasAttachments) {
        this.hasAttachments = hasAttachments;
    }
}
