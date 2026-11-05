package net.jforum.repository;

import java.util.Optional;
import net.jforum.domain.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AttachmentExtensionRepository extends MongoRepository<AttachmentExtension, Integer> {

    Optional<AttachmentExtension> findByExtensionIgnoreCase(String extension);
}
