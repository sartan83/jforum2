package net.jforum.repository;

import java.util.Optional;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ApiKeyRepository extends MongoRepository<ApiKey, Integer> {

    Optional<ApiKey> findByApiKey(String apiKey);
}
