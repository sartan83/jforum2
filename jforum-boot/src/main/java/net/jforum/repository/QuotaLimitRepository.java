package net.jforum.repository;

import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface QuotaLimitRepository extends MongoRepository<QuotaLimit, Integer> {
}
