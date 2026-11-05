package net.jforum.pm;

import java.time.Instant;

import net.jforum.domain.PrivateMessage;
import net.jforum.domain.PrivateMessageType;

/** Template-friendly private message with resolved usernames and formatted text. */
public class PmView {

    private final PrivateMessage pm;
    private final String fromUsername;
    private final String toUsername;
    private final String textHtml;
    private final String signatureHtml;

    public PmView(PrivateMessage pm, String fromUsername, String toUsername, String textHtml, String signatureHtml) {
        this.pm = pm;
        this.fromUsername = fromUsername;
        this.toUsername = toUsername;
        this.textHtml = textHtml;
        this.signatureHtml = signatureHtml;
    }

    public Integer getId() {
        return pm.getId();
    }

    public int getType() {
        return pm.getType() == null ? 0 : pm.getType().legacyCode();
    }

    public boolean isNew() {
        return pm.getType() == PrivateMessageType.NEW;
    }

    public boolean isUnread() {
        return pm.getType() == PrivateMessageType.NEW || pm.getType() == PrivateMessageType.UNREAD;
    }

    public String getSubject() {
        return pm.getSubject();
    }

    public String getText() {
        return pm.getText();
    }

    public Instant getDate() {
        return pm.getDate();
    }

    public Integer getFromUserId() {
        return pm.getFromUserId();
    }

    public Integer getToUserId() {
        return pm.getToUserId();
    }

    public String getFromUsername() {
        return fromUsername;
    }

    public String getToUsername() {
        return toUsername;
    }

    public String getTextHtml() {
        return textHtml;
    }

    public String getSignatureHtml() {
        return signatureHtml;
    }

    public boolean isBbCodeEnabled() {
        return pm.isBbCodeEnabled();
    }

    public boolean isSmiliesEnabled() {
        return pm.isSmiliesEnabled();
    }

    public boolean isSignatureEnabled() {
        return pm.isSignatureEnabled();
    }
}
