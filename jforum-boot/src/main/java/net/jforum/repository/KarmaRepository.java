package net.jforum.repository;

import java.util.List;
import java.util.Optional;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface KarmaRepository extends MongoRepository<Karma, Integer> {

    Optional<Karma> findByPostIdAndFromUserId(Integer postId, Integer fromUserId);

    List<Karma> findByPostUserId(Integer postUserId);

    List<Karma> findByPostId(Integer postId);

    List<Karma> findByRateDateBetween(java.time.Instant from, java.time.Instant to);
}
