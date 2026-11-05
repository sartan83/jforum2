package net.jforum.attachment;

/** Upload validation error; {@link #getMessageKey()} is an i18n key (legacy Attachments.* messages). */
public class AttachmentException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    public AttachmentException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
