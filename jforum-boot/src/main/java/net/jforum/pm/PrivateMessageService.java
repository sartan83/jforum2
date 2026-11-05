package net.jforum.pm;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.jforum.config.ConfigKeys;
import net.jforum.config.ForumConfig;
import net.jforum.domain.PrivateMessage;
import net.jforum.domain.PrivateMessageType;
import net.jforum.domain.User;
import net.jforum.format.PostFormatter;
import net.jforum.repository.PrivateMessageRepository;
import net.jforum.repository.UserRepository;
import net.jforum.support.SequenceService;
import net.jforum.user.ForumMailer;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Legacy PrivateMessageDAO semantics: sending stores two copies (sender's SENT copy, recipient's NEW copy),
 * reading a NEW message marks it READ, and the recipient's User.unreadPrivateMessages counter tracks NEW copies.
 */
@Service
public class PrivateMessageService {

    static final Set<PrivateMessageType> INBOX_TYPES = EnumSet.of(PrivateMessageType.NEW, PrivateMessageType.READ, PrivateMessageType.UNREAD);
    static final Set<PrivateMessageType> SENT_TYPES = EnumSet.of(PrivateMessageType.SENT);
    static final String SEQUENCE = "private_messages";

    private final PrivateMessageRepository messages;
    private final UserRepository users;
    private final SequenceService sequences;
    private final MongoTemplate mongo;
    private final PostFormatter formatter;
    private final ForumConfig config;
    private final ForumMailer mailer;

    public PrivateMessageService(PrivateMessageRepository messages, UserRepository users, SequenceService sequences, MongoTemplate mongo,
                                 PostFormatter formatter, ForumConfig config, ForumMailer mailer) {
        this.messages = messages;
        this.users = users;
        this.sequences = sequences;
        this.mongo = mongo;
        this.formatter = formatter;
        this.config = config;
        this.mailer = mailer;
    }

    public List<PmView> inbox(int userId) {
        return views(messages.findByToUserIdAndTypeInOrderByDateDesc(userId, INBOX_TYPES), false);
    }

    public List<PmView> sentbox(int userId) {
        return views(messages.findByFromUserIdAndTypeInOrderByDateDesc(userId, SENT_TYPES), false);
    }

    public Optional<PrivateMessage> findById(Integer id) {
        return id == null ? Optional.empty() : messages.findById(id);
    }

    public static boolean canRead(PrivateMessage pm, int userId) {
        return pm.getToUserId() != null && pm.getToUserId() == userId || pm.getFromUserId() != null && pm.getFromUserId() == userId;
    }

    /** Stores the sender copy (SENT) and the recipient copy (NEW), then bumps the recipient's unread counter. */
    public PrivateMessage send(User from, User to, String subject, String text, boolean bbCode, boolean smilies, boolean signature, String ip) {
        Instant now = Instant.now();
        PrivateMessage sent = build(from, to, subject, text, bbCode, smilies, signature, ip, now);
        sent.setType(PrivateMessageType.SENT);
        messages.save(sent);

        PrivateMessage inbox = build(from, to, subject, text, bbCode, smilies, signature, ip, now);
        inbox.setType(PrivateMessageType.NEW);
        messages.save(inbox);

        mongo.updateFirst(Query.query(Criteria.where("_id").is(to.getId())),
            new Update().inc("unreadPrivateMessages", 1).inc("newPrivateMessages", 1), User.class);

        if (config.getBool(ConfigKeys.MAIL_NOTIFY_ANSWERS) && to.isNotifyPrivateMessages() && to.getEmail() != null && !to.getEmail().isBlank()) {
            mailer.send(to.getEmail(), "mail.newPm.subject", "newPrivateMessage.txt",
                Map.of("user", to, "path", mailer.absoluteUrl("/pm/read/" + inbox.getId() + ".page")));
        }
        return inbox;
    }

    private PrivateMessage build(User from, User to, String subject, String text, boolean bbCode, boolean smilies, boolean signature,
                                 String ip, Instant now) {
        PrivateMessage pm = new PrivateMessage();
        pm.setId(sequences.next(SEQUENCE));
        pm.setFromUserId(from.getId());
        pm.setToUserId(to.getId());
        pm.setSubject(subject);
        pm.setText(text);
        pm.setDate(now);
        pm.setFromIp(ip);
        pm.setBbCodeEnabled(bbCode);
        pm.setHtmlEnabled(false);
        pm.setSmiliesEnabled(smilies);
        pm.setSignatureEnabled(signature);
        return pm;
    }

    /** Marks the recipient copy as read (legacy updateType) and decrements the unread counter. @return true if it was new */
    public boolean markRead(PrivateMessage pm, User reader) {
        if (pm.getType() != PrivateMessageType.NEW || pm.getToUserId() == null || !pm.getToUserId().equals(reader.getId())) {
            return false;
        }
        pm.setType(PrivateMessageType.READ);
        messages.save(pm);
        decrementUnread(reader, 1);
        return true;
    }

    /**
     * Legacy PrivateMessagesModel.isDeleteAllowed: the sender may delete its SENT copy, the recipient its inbox copy.
     * @return the number of deleted messages
     */
    public int delete(Collection<Integer> ids, User user) {
        int deleted = 0;
        int unread = 0;
        for (PrivateMessage pm : messages.findByIdIn(ids)) {
            boolean ownSent = user.getId().equals(pm.getFromUserId()) && pm.getType() == PrivateMessageType.SENT;
            boolean ownInbox = user.getId().equals(pm.getToUserId()) && INBOX_TYPES.contains(pm.getType());
            if (ownSent || ownInbox) {
                if (ownInbox && pm.getType() == PrivateMessageType.NEW) {
                    unread++;
                }
                messages.delete(pm);
                deleted++;
            }
        }
        if (unread > 0) {
            decrementUnread(user, unread);
        }
        return deleted;
    }

    private void decrementUnread(User user, int by) {
        users.findById(user.getId()).ifPresent(fresh -> {
            int value = Math.max(0, fresh.getUnreadPrivateMessages() - by);
            mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId())),
                new Update().set("unreadPrivateMessages", value).set("newPrivateMessages", Math.max(0, fresh.getNewPrivateMessages() - by)),
                User.class);
            user.setUnreadPrivateMessages(value);
        });
    }

    public PmView view(PrivateMessage pm) {
        return views(List.of(pm), true).get(0);
    }

    private List<PmView> views(List<PrivateMessage> list, boolean format) {
        Set<Integer> ids = list.stream().flatMap(pm -> java.util.stream.Stream.of(pm.getFromUserId(), pm.getToUserId()))
            .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, User> byId = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(pm -> {
            User from = byId.get(pm.getFromUserId());
            User to = byId.get(pm.getToUserId());
            String html = format ? formatter.format(pm.getText(), pm.isHtmlEnabled(), pm.isBbCodeEnabled(), pm.isSmiliesEnabled()) : null;
            String sig = format && pm.isSignatureEnabled() && from != null && from.getSignature() != null && !from.getSignature().isBlank()
                ? formatter.format(from.getSignature(), false, true, true) : null;
            return new PmView(pm, from == null ? "" : from.getUsername(), to == null ? "" : to.getUsername(), html, sig);
        }).toList();
    }

    public String preview(String text, boolean bbCode, boolean smilies) {
        return formatter.format(text, false, bbCode, smilies);
    }
}
