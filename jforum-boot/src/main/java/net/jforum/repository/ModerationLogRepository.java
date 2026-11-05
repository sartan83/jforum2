package net.jforum.repository;

import net.jforum.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ModerationLogRepository extends MongoRepository<ModerationLog, Integer> {

    Page<ModerationLog> findAllByOrderByDateDesc(Pageable pageable);
}
