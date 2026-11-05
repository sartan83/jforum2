package net.jforum.repository;

import java.util.Collection;
import java.util.List;
import net.jforum.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicRepository extends MongoRepository<Topic, Integer> {

    Page<Topic> findByForumId(Integer forumId, Pageable pageable);

    long countByForumId(Integer forumId);

    List<Topic> findByForumIdInAndMovedIdIsNull(Collection<Integer> forumIds, Pageable pageable);

    Page<Topic> findByUserId(Integer userId, Pageable pageable);
}
