package net.jforum.repository;

import java.util.List;
import java.util.Optional;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ForumWatchRepository extends MongoRepository<ForumWatch, String> {

    List<ForumWatch> findByForumId(Integer forumId);

    Optional<ForumWatch> findByForumIdAndUserId(Integer forumId, Integer userId);
}
