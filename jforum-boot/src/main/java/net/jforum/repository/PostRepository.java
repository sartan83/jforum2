package net.jforum.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.jforum.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PostRepository extends MongoRepository<Post, Integer> {

    Page<Post> findByTopicIdAndModerateFalse(Integer topicId, Pageable pageable);

    Page<Post> findByTopicId(Integer topicId, Pageable pageable);

    long countByTopicIdAndModerateFalse(Integer topicId);

    long countByTopicIdAndModerateFalseAndTimeLessThan(Integer topicId, Instant time);

    List<Post> findByTopicIdOrderByTimeAsc(Integer topicId);

    List<Post> findByModerateTrue();

    Page<Post> findByUserId(Integer userId, Pageable pageable);

    long countByForumIdAndModerateFalse(Integer forumId);

    void deleteByTopicId(Integer topicId);

    long countByTopicId(Integer topicId);

    Optional<Post> findFirstByTopicIdAndModerateFalseOrderByIdDesc(Integer topicId);

    Optional<Post> findFirstByTopicIdOrderByIdAsc(Integer topicId);

    Optional<Post> findFirstByForumIdAndModerateFalseOrderByIdDesc(Integer forumId);

    long countByTopicIdAndModerateFalseAndIdLessThan(Integer topicId, Integer id);

    long countByUserId(Integer userId);
}
