package net.jforum.repository;

import java.util.List;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoryRepository extends MongoRepository<Category, Integer> {

    List<Category> findAllByOrderByOrderAsc();
}
