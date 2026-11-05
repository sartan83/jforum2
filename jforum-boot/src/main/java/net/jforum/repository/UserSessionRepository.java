package net.jforum.repository;

import java.time.Instant;
import java.util.List;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserSessionRepository extends MongoRepository<UserSession, String> {

    List<UserSession> findByLastAccessAfter(Instant since);

    void deleteByLastAccessBefore(Instant before);
}
