package net.jforum.repository;

import java.util.List;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RankingRepository extends MongoRepository<Ranking, Integer> {

    List<Ranking> findAllByOrderByMinAsc();
}
