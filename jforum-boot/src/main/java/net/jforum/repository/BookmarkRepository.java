package net.jforum.repository;

import java.util.List;
import java.util.Optional;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BookmarkRepository extends MongoRepository<Bookmark, Integer> {

    List<Bookmark> findByUserId(Integer userId);

    Optional<Bookmark> findByUserIdAndRelationTypeAndRelationId(Integer userId, BookmarkType relationType, Integer relationId);
}
