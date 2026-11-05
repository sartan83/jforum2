package net.jforum.forum;

import net.jforum.domain.ForumWatch;
import net.jforum.repository.ForumWatchRepository;
import org.springframework.stereotype.Service;

/** Forum watches (legacy ForumDAO subscribe/removeSubscription/isUserSubscribed). */
@Service
public class ForumWatchService {

    private final ForumWatchRepository watches;

    public ForumWatchService(ForumWatchRepository watches) {
        this.watches = watches;
    }

    public boolean isWatching(int forumId, int userId) {
        return watches.findByForumIdAndUserId(forumId, userId).isPresent();
    }

    public void watch(int forumId, int userId) {
        if (!isWatching(forumId, userId)) {
            ForumWatch w = new ForumWatch();
            w.setForumId(forumId);
            w.setUserId(userId);
            watches.save(w);
        }
    }

    public void unwatch(int forumId, int userId) {
        watches.deleteByForumIdAndUserId(forumId, userId);
    }
}
