package net.jforum.repository;

import java.util.List;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ForumRepository extends MongoRepository<Forum, Integer> {

    List<Forum> findByCategoryIdOrderByOrderAsc(Integer categoryId);

    List<Forum> findAllByOrderByOrderAsc();
}
