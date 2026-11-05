package net.jforum.repository;

import java.util.List;
import java.util.Optional;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicWatchRepository extends MongoRepository<TopicWatch, String> {

    List<TopicWatch> findByTopicId(Integer topicId);

    Optional<TopicWatch> findByTopicIdAndUserId(Integer topicId, Integer userId);

    void deleteByTopicId(Integer topicId);

    void deleteByTopicIdAndUserId(Integer topicId, Integer userId);
}
